package org.basex.query.func;

import static org.basex.query.QueryText.*;
import static org.basex.util.Token.*;
import org.basex.query.QueryException;
import org.basex.query.expr.Calc;
import org.basex.query.item.Item;
import org.basex.query.item.Itr;
import org.basex.query.item.Str;
import org.basex.query.util.Err;
import org.basex.util.Array;

/**
 * Number formatter.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-10, ISC License
 * @author Christian Gruen
 */
final class FNFormatNum {
  /** Infinity. */
  private static final String INF = "Infinity";
  /** NaN. */
  private static final String NAN = "NaN";
  /** Pattern-separator-sign. */
  private static final char PATTERN = ';';

  // active characters

  /** Mandatory-digit-sign. */
  private static final String DIGITS = "0123456789";
  /** Decimal-separator-sign. */
  private static final char DECIMAL = '.';
  /** Grouping-separator-sign. */
  private static final char GROUP = ',';
  /** Optional-digit-sign. */
  private static final char OPTIONAL = '#';
  /** Active characters. */
  private static final String ACTIVE = DIGITS + DECIMAL + GROUP + OPTIONAL;

  // passive characters

  /** Minus-sign. */
  private static final char MINUS = '-';
  /** Percent-sign. */
  private static final char PERCENT = '%';
  /** Permille-sign. */
  private static final char PERMILLE = '\u2030';
  /** Passive characters. */
  private static final String PASSIVE = "" + MINUS + PERCENT + PERMILLE;

  /** Number to be formatted. */
  private final Item number;
  /** Picture. */
  private final String picture;

  /**
   * Constructor.
   * @param it number
   * @param p picture
   */
  FNFormatNum(final Item it, final String p) {
    number = it;
    picture = p;
  }

  /**
   * Returns a formatted number.
   * @return string
   * @throws QueryException query exception
   */
  Str format() throws QueryException {
    // find pattern separator and sub-patterns
    final String[] sub = picture.split(String.valueOf(PATTERN));
    if(sub.length > 2) errPic(picture);

    // check and analyze patterns
    check(sub);
    final Picture[] pics = analyze(sub);

    // return formatted string
    return Str.get(token(format(number, pics)));
  }

  /**
   * Checks the syntax of the specified patterns.
   * @param patterns patterns
   * @throws QueryException query exception
   */
  private void check(final String[] patterns) throws QueryException {
    for(final String pat : patterns) {
      boolean frac = false, pas = false, act = false;
      boolean dig = false, opt1 = false, opt2 = false;
      int pc = 0, pm = 0;

      // loop through all characters
      for(int i = 0; i < pat.length(); i++) {
        final char ch = pat.charAt(i);
        final boolean a = ACTIVE.indexOf(ch) != -1;
        final boolean p = PASSIVE.indexOf(ch) != -1;

        if(ch == DECIMAL) {
          // more than 1 decimal sign?
          if(frac) errPic(pat);
          frac = true;
        } else if(ch == GROUP) {
          // adjacent decimal sign?
          if(i > 0 && pat.charAt(i - 1) == DECIMAL ||
             i + 1 < pat.length() && pat.charAt(i + 1) == DECIMAL) errPic(pat);
        } else if(ch == PERCENT) {
          pc++;
        } else if(ch == PERMILLE) {
          pm++;
        } else if(ch == OPTIONAL) {
          if(!frac) {
            // integer part, and optional sign after digit?
            if(dig) errPic(pat);
            opt1 = true;
          } else {
            opt2 = true;
          }
        } else if(DIGITS.indexOf(ch) != -1) {
          // fractional part, and digit after optional sign?
          if(frac && opt2) errPic(pat);
          dig = true;
        } else if(ch != MINUS) {
          // unknown character
          errPic(pat);
        }

        // passive character with preceding and following active character?
        if(a && pas && act) errPic(pat);
        // will be assigned if active characters were found
        if(act) pas |= p;
        act |= a;
      }

      // more than 1 percent and permille sign?
      if(pc > 1 || pm > 1 || pc + pm > 1) errPic(pat);
      // no optional sign or digit?
      if(!opt1 && !opt2 && !dig) errPic(pat);
    }
  }

  /**
   * Analyzes the specified patterns.
   * @param patterns patterns
   * @return picture variables
   */
  private Picture[] analyze(final String[] patterns) {
    // pictures
    final Picture[] pics = new Picture[patterns.length];

    // analyze patterns
    for(int s = 0; s < patterns.length; s++) {
      final String pat = patterns[s];
      final Picture pic = new Picture();

      // position (integer/fractional)
      int pos = 0;
      boolean act = false;
      int[] opt = new int[2];

      // loop through all characters
      for(int i = 0; i < pat.length(); i++) {
        final char ch = pat.charAt(i);
        final boolean a = ACTIVE.indexOf(ch) != -1;

        if(ch == DECIMAL) {
          pos++;
          act = false;
        } else if(ch == OPTIONAL) {
          opt[pos]++;
        } else if(ch == GROUP) {
          pic.group[pos] = Array.add(pic.group[pos], pic.min[pos] + opt[pos]);
        } else if(DIGITS.indexOf(ch) != -1) {
          pic.min[pos]++;
        } else {
          // passive characters
          pic.pc |= ch == PERCENT;
          pic.pm |= ch == PERMILLE;
          // prefixes/suffixes
          if(pos == 0 ^ act) pic.fix[pos] += ch;
        }
        act |= a;
      }
      // finalize group positions
      for(int g = 0; g < pic.group[0].length; g++) {
        pic.group[0][g] = pic.min[0] + opt[0] - pic.group[0][g];
      }
      pic.maxFrac = pic.min[1] + opt[1];
      pics[s] = pic;
    }
    return pics;
  }

  /**
   * Formats the specified number and returns a string representation.
   * @param it item
   * @param pics pictures
   * @return picture variables
   * @throws QueryException query exception
   */
  private String format(final Item it, final Picture[] pics)
      throws QueryException {

    final double d = it.dbl();
    final Picture pic = pics[d < 0 && pics.length == 2 ? 1 : 0];
    if(d < 0 && pics.length == 1) pic.fix[0] = String.valueOf(MINUS);

    // return results for NaN and infinity
    if(Double.isNaN(d)) return NAN;
    if(Double.isInfinite(d)) return pic.fix[0] + INF + pic.fix[1];

    // convert and round number
    Item num = it;
    if(pic.pc) num = Calc.MULT.ev(num, Itr.get(100));
    if(pic.pm) num = Calc.MULT.ev(num, Itr.get(1000));
    num = FNNum.abs(FNNum.round(num, num.dbl(), pic.maxFrac, true));

    // convert to string representation
    final String str = num.toString();

    // integer/fractional separator
    int sp = str.indexOf(DECIMAL);
    //if(sp == -1) sp = str.length();

    // create integer part
    final StringBuilder pre = new StringBuilder();
    final int il = sp == -1 ? str.length() : sp;
    for(int i = il; i < pic.min[0]; i++) pre.append('0');
    pre.append(str.substring(0, il));
    // add grouping separators
    int pl = pre.length();
    for(int i = 0; i < pic.group[0].length; i++) {
      int pos = pl - pic.group[0][i];
      if(pos > 0) pre.insert(pos, GROUP);
    }

    // create fractional part
    final StringBuilder suf = new StringBuilder();
    final int fl = sp == -1 ? 0 : str.length() - il - 1;
    if(fl != 0) suf.append(str.substring(sp + 1));
    for(int i = fl; i < pic.min[1]; i++) suf.append('0');

    int sl = suf.length();
    for(int i = pic.group[1].length - 1; i >= 0; i--) {
      int pos = pic.group[1][i];
      if(pos < sl) suf.insert(pos, GROUP);
    }

    final StringBuilder res = new StringBuilder(pic.fix[0]);
    res.append(pre);
    if(suf.length() != 0) res.append(DECIMAL).append(suf);
    return res.append(pic.fix[1]).toString();
  }

  /**
   * Returns an error for the specified picture.
   * @param pic picture
   * @throws QueryException query exception
   */
  private static void errPic(final String pic) throws QueryException {
    Err.or(FORMPIC, pic);
  }

  /** Picture variables. */
  static class Picture {
    /** prefix/suffix. */
    String[] fix = { "", "" };
    /** integer/fractional-part-grouping-positions. */
    int[][] group = { {}, {} };
    /** minimum-integer/fractional-part-size. */
    int[] min = { 0, 0 };
    /** maximum-fractional-part-size. */
    int maxFrac;
    /** percent flag. */
    boolean pc;
    /** per-mille flag. */
    boolean pm;
  }
}
