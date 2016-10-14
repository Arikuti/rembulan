/*
 * Copyright 2016 Miroslav Janíček
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sandius.rembulan.lib.impl;

import net.sandius.rembulan.ByteString;
import net.sandius.rembulan.ByteStringBuilder;
import net.sandius.rembulan.Conversions;
import net.sandius.rembulan.LuaFormat;
import net.sandius.rembulan.LuaRuntimeException;
import net.sandius.rembulan.Metatables;
import net.sandius.rembulan.PlainValueTypeNamer;
import net.sandius.rembulan.Table;
import net.sandius.rembulan.impl.NonsuspendableFunctionException;
import net.sandius.rembulan.impl.UnimplementedFunction;
import net.sandius.rembulan.lib.BadArgumentException;
import net.sandius.rembulan.lib.BasicLib;
import net.sandius.rembulan.lib.StringLib;
import net.sandius.rembulan.runtime.AbstractFunction0;
import net.sandius.rembulan.runtime.Dispatch;
import net.sandius.rembulan.runtime.ExecutionContext;
import net.sandius.rembulan.runtime.IllegalOperationAttemptException;
import net.sandius.rembulan.runtime.LuaFunction;
import net.sandius.rembulan.runtime.ResolvedControlThrowable;
import net.sandius.rembulan.runtime.UnresolvedControlThrowable;
import net.sandius.rembulan.util.ByteIterator;
import net.sandius.rembulan.util.Check;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultStringLib extends StringLib {

	private final LuaFunction _pack;
	private final LuaFunction _packsize;
	private final LuaFunction _unpack;

	public DefaultStringLib() {
		this._pack = new UnimplementedFunction("string.pack");  // TODO
		this._packsize = new UnimplementedFunction("string.packsize");  // TODO
		this._unpack = new UnimplementedFunction("string.unpack");  // TODO
	}

	@Override
	public LuaFunction _byte() {
		return Byte.INSTANCE;
	}

	@Override
	public LuaFunction _char() {
		return Char.INSTANCE;
	}

	@Override
	public LuaFunction _dump() {
		return Dump.INSTANCE;
	}

	@Override
	public LuaFunction _find() {
		return Find.INSTANCE;
	}

	@Override
	public LuaFunction _format() {
		return Format.INSTANCE;
	}

	@Override
	public LuaFunction _gmatch() {
		return GMatch.INSTANCE;
	}

	@Override
	public LuaFunction _gsub() {
		return GSub.INSTANCE;
	}

	@Override
	public LuaFunction _len() {
		return Len.INSTANCE;
	}

	@Override
	public LuaFunction _lower() {
		return Lower.INSTANCE;
	}

	@Override
	public LuaFunction _match() {
		return Match.INSTANCE;
	}

	@Override
	public LuaFunction _pack() {
		return _pack;
	}

	@Override
	public LuaFunction _packsize() {
		return _packsize;
	}

	@Override
	public LuaFunction _rep() {
		return Rep.INSTANCE;
	}

	@Override
	public LuaFunction _reverse() {
		return Reverse.INSTANCE;
	}

	@Override
	public LuaFunction _sub() {
		return Sub.INSTANCE;
	}

	@Override
	public LuaFunction _unpack() {
		return _unpack;
	}

	@Override
	public LuaFunction _upper() {
		return Upper.INSTANCE;
	}

	private static int lowerBound(int i, int len) {
		int j = i < 0 ? len + i + 1 : i;
		return Math.max(1, j);
	}

	private static int upperBound(int i, int len) {
		int j = i < 0 ? len + i + 1 : i;
		return Math.max(0, Math.min(len, j));
	}

	private static byte toLower(byte b) {
		int c = b & 0xff;
		// FIXME: dealing with ASCII only
		return c >= 'A' && c <= 'Z' ? (byte) (c - (int) 'A' + (int) 'a') : b;
	}

	private static ByteString toLowerCase(ByteString s) {
		boolean changed = false;

		ByteStringBuilder bld = new ByteStringBuilder();
		ByteIterator it = s.byteIterator();
		while (it.hasNext()) {
			byte b = it.nextByte();
			byte c = toLower(b);
			changed |= b != c;
			bld.append(c);
		}

		return changed ? bld.toByteString() : s;
	}

	private static byte toUpper(byte b) {
		int c = b & 0xff;
		// FIXME: dealing with ASCII only
		return c >= 'a' && c <= 'z' ? (byte) (c - (int) 'a' + (int) 'A') : b;
	}

	private static ByteString toUpperCase(ByteString s) {
		boolean changed = false;

		ByteStringBuilder bld = new ByteStringBuilder();
		ByteIterator it = s.byteIterator();
		while (it.hasNext()) {
			byte b = it.nextByte();
			byte c = toUpper(b);
			changed |= b != c;
			bld.append(c);
		}

		return changed ? bld.toByteString() : s;
	}

	public static class Byte extends AbstractLibFunction {

		public static final Byte INSTANCE = new Byte();

		@Override
		protected String name() {
			return "byte";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			ByteString s = args.nextString();
			int i = args.optNextInt(1);
			int j = args.optNextInt(i);

			int len = s.length();

			i = lowerBound(i, len);
			j = upperBound(j, len);

			List<Object> buf = new ArrayList<>();
			for (int idx = i; idx <= j; idx++) {
				int c = s.byteAt(idx - 1) & 0xff;
				buf.add(Long.valueOf(c));
			}
			context.getReturnBuffer().setToContentsOf(buf);
		}

	}

	public static class Char extends AbstractLibFunction {

		public static final Char INSTANCE = new Char();

		@Override
		protected String name() {
			return "char";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			byte[] bytes = new byte[args.size()];

			for (int i = 0; i < bytes.length; i++) {
				bytes[i] = (byte) args.nextIntRange("value", 0, 255);
			}

			ByteString s = ByteString.copyOf(bytes);
			context.getReturnBuffer().setTo(s);
		}

	}

	public static class Dump extends AbstractLibFunction {

		public static final Dump INSTANCE = new Dump();

		@Override
		protected String name() {
			return "dump";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			LuaFunction f = args.nextFunction();
			boolean strip = args.optNextBoolean(false);

			throw new IllegalOperationAttemptException("unable to dump given function");
		}

	}

	public static class Find extends AbstractLibFunction {

		public static final Find INSTANCE = new Find();

		@Override
		protected String name() {
			return "find";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			String s = args.nextString().toString();  // FIXME
			String pattern = args.nextString().toString();  // FIXME
			int init = args.optNextInt(1);
			boolean plain = args.optNextBoolean(false);

			init = lowerBound(init, s.length());

			if (plain) {
				// find a substring
				int at = s.indexOf(pattern, init - 1);
				if (at >= 0) {
					context.getReturnBuffer().setTo(
							(long) (at + 1),
							(long) (at + pattern.length()));
				}
				else {
					context.getReturnBuffer().setTo(null);
				}
			}
			else {
				// find a pattern
				StringPattern pat = StringPattern.fromString(pattern);

				StringPattern.Match m = pat.match(s, init - 1);

				if (m != null) {
					List<Object> result = new ArrayList<>();
					result.add((long) (m.beginIndex() + 1));
					result.add((long) m.endIndex());
					result.addAll(m.captures());
					context.getReturnBuffer().setToContentsOf(result);
				}
				else {
					// no match
					context.getReturnBuffer().setTo(null);
				}
			}
		}

	}

	public static class Format extends AbstractLibFunction {

		public static final Format INSTANCE = new Format();

		@Override
		protected String name() {
			return "format";
		}

		private static class SuspendedState {

			public final String fmt;  // the format string
			public final String str;  // the string so far
			public final ArgumentIterator args;
			public final int index;

			// flags for the suspended %s
			public final int width;
			public final int flags;
			public final int precision;

			public SuspendedState(String fmt, String str, ArgumentIterator args, int index, int width, int flags, int precision) {
				this.fmt = fmt;
				this.str = str;
				this.args = args;
				this.index = index;
				this.width = width;
				this.flags = flags;
				this.precision = precision;
			}

		}

		private static String optionToString(char c) {
			if (Character.isLetterOrDigit(c)) {
				return "%" + c;
			}
			else {
				return "%<\\" + ((int) c) + ">";
			}
		}

		private static void repeatChar(char c, int num, StringBuilder bld) {
			for (int i = 0; i < num; i++) {
				bld.append(c);
			}
		}

		private static String padLeft(String s, char c, int width) {
			int diff = width - s.length();

			if (diff > 0) {
				StringBuilder bld = new StringBuilder();
				repeatChar(c, diff, bld);
				bld.append(s);
				return bld.toString();
			}
			else {
				return s;
			}
		}

		private static String padRight(String s, char c, int width) {
			int diff = width - s.length();

			if (diff > 0) {
				StringBuilder bld = new StringBuilder();
				bld.append(s);
				repeatChar(c, diff, bld);
				return bld.toString();
			}
			else {
				return s;
			}
		}

		private static final long L_1E18  = 1000000000000000000L;
		private static final long L_9E18  =  9 * L_1E18;
		private static final long L_10E18 = 10 * L_1E18;  // overflows, and that's the point

		public static String longToUnsignedString(long x) {

			// Maximum value representable by signed long is    (2^63 - 1)
			//                             by unsigned long is  (2^64 - 1)
			//
			// Now,
			//        9e18 < (2^63 - 1) < 10e18 < (2^64 - 1) < 20e18
			//
			// If signed(x) >= 0, then signed(x) == unsigned(x).
			// If signed(x) < 0, then unsigned(x) >= 2^63, and therefore unsigned(x) > unsigned(9e18).
			// Now we only need to check whether unsigned(x) >= unsigned(10e18) -- if so,
			// the leftmost digit is necessarily '1' (since 20e18 > 2^64), followed by 19 digits;
			// otherwise, the leftmost digit is '9', followed by 18 digits.
			// In 2's complement, for a, b such that both unsigned(a) >= 2^63 and unsigned(b) >= 2^63,
			// (signed(a) < signed(b)) iff (unsigned(a) < unsigned(b)),
			// so the test is equivalent to signed(x) >= signed(10e18).

			return x >= 0
					? Long.toString(x)
					: (x >= L_10E18
							? '1' + padLeft(Long.toString(x - L_10E18), '0', 19)
							: '9' + padLeft(Long.toString(x - L_9E18), '0', 18));
		}

		private int literal(String fmt, int from, StringBuilder bld) {
			int index = from;
			while (index < fmt.length()) {
				char c = fmt.charAt(index++);

				if (c != '%') {
					bld.append(c);
				}
				else {
					if (index < fmt.length() && fmt.charAt(index) == '%') {
						// literal '%'
						bld.append('%');
						index += 1;
					}
					else {
						return index;
					}
				}

			}
			return -1;
		}

		private static IllegalArgumentException invalidOptionException(char c) {
			return new IllegalArgumentException("invalid option '" + optionToString(c) + "' to 'format'");
		}

		private static int setFlag(int flags, int mask) {
			if ((flags & mask) != 0) {
				throw new IllegalArgumentException("illegal format (repeated flags)");
			}
			return flags | mask;
		}

		private static boolean hasFlag(int flags, int mask) {
			return (flags & mask) != 0;
		}

		private static String sign(boolean nonNegative, int flags) {
			return nonNegative
					? (hasFlag(flags, FLAG_SIGN_ALWAYS)
							? "+"
							: (hasFlag(flags, FLAG_ZERO_PAD)
									? " "
									: ""))
					: "-";
		}

		private static String altForm(long value, int flags, String prefix) {
			return value != 0 && hasFlag(flags, FLAG_ALT_FORM) ? prefix : "";
		}

		private static String padded(int precision, String digits) {
			return precision >= 0
					? padLeft("0".equals(digits) ? "" : digits, '0', precision)
					: digits;
		}

		private static String trimmed(int precision, String chars) {
			return precision >= 0
					? chars.substring(0, Math.min(chars.length(), precision))
					: chars;
		}

		private static String justified(int width, int flags, String digits) {
			return width >= 0
					? (hasFlag(flags, FLAG_LEFTJUSTIFY)
							? padRight(digits, ' ', width)
							: padLeft(digits, ' ', width))
					: digits;
		}

		private static final int FLAG_LEFTJUSTIFY = 1 << 1;
		private static final int FLAG_SIGN_ALWAYS = 1 << 2;
		private static final int FLAG_SIGN_SPACE = 1 << 3;
		private static final int FLAG_ZERO_PAD = 1 << 4;
		private static final int FLAG_ALT_FORM = 1 << 5;

		private static void format_signed_integer(StringBuilder bld, ArgumentIterator args, char spec, int width, int flags, int precision) {
			long l = args.nextInteger();

			String ls = LuaFormat.toString(l);
			String digits = l < 0 ? ls.substring(1) : ls;  // ignore the sign, we'll re-attach it later
			bld.append(justified(width, flags,
					sign(l >= 0, flags) + padded(precision, digits)));
		}

		private static void format_unsigned_integer(StringBuilder bld, ArgumentIterator args, int width, int flags, int precision) {
			long l = args.nextInteger();

			String digits = longToUnsignedString(l);
			bld.append(justified(width, flags, padded(precision, digits)));
		}

		private static void format_octal_integer(StringBuilder bld, ArgumentIterator args, int width, int flags, int precision) {
			long l = args.nextInteger();

			String digits = Long.toOctalString(l);
			bld.append(justified(width, flags, altForm(l, flags, "0") + padded(precision, digits)));
		}

		private static void format_hex_integer(StringBuilder bld, ArgumentIterator args, boolean uppercase, int width, int flags, int precision) {
			long l = args.nextInteger();

			String digits = Long.toHexString(l);
			String lowerCaseResult = justified(width, flags,
					altForm(l, flags, "0x") + padded(precision, digits));

			bld.append(uppercase ? lowerCaseResult.toUpperCase() : lowerCaseResult);
		}

		private static void format_char(StringBuilder bld, ArgumentIterator args, int width, int flags) {
			bld.append(justified(width, flags, Character.toString((char) args.nextInteger())));
		}

		private static void format_float(StringBuilder bld, ArgumentIterator args, char spec, int width, int flags, int precision) {
			double v = args.nextFloat();

			if (Double.isNaN(v) || Double.isInfinite(v)) {
				final ByteString chars;

				chars = Double.isNaN(v)
						? LuaFormat.NAN
						: ByteString.of(sign(v > 0, flags) + LuaFormat.INF);

				bld.append(justified(width, flags, chars.toString()));
			}
			else {
				StringBuilder fmtBld = new StringBuilder();
				fmtBld.append('%');
				if (hasFlag(flags, FLAG_LEFTJUSTIFY)) fmtBld.append('-');
				if (hasFlag(flags, FLAG_SIGN_ALWAYS)) fmtBld.append('+');
				if (hasFlag(flags, FLAG_SIGN_SPACE)) fmtBld.append(' ');
				if (hasFlag(flags, FLAG_ZERO_PAD)) fmtBld.append('0');
				if (hasFlag(flags, FLAG_ALT_FORM)) fmtBld.append('#');

				if (width > 0) fmtBld.append(width);
				// width required by Formatter, but not supplied
				else if (hasFlag(flags, FLAG_ZERO_PAD)) fmtBld.append('1');

				if (precision > 0) fmtBld.append('.').append(precision);
				fmtBld.append(spec);
				String formatted = String.format(fmtBld.toString(), v);

				if (spec == 'a' || spec == 'A') {
					// insert the '+' sign to the exponent
					int p = formatted.indexOf(spec == 'a' ? 'p' : 'P') + 1;
					if (formatted.charAt(p) != '-') {
						formatted = formatted.substring(0, p) + '+' + formatted.substring(p);
					}
				}

				bld.append(formatted);
			}
		}

		private void format_s(ExecutionContext context, String fmt, StringBuilder bld, ArgumentIterator args, int index, int width, int flags, int precision)
				throws ResolvedControlThrowable {
			Object v = args.nextAny();
			final String s;

			ByteString stringValue = Conversions.stringValueOf(v);
			if (stringValue != null) {
				s = stringValue.toString();
			}
			else {
				Object metamethod = Metatables.getMetamethod(context, BasicLib.MT_TOSTRING, v);
				if (metamethod != null) {
					// call __tostring
					try {
						Dispatch.call(context, metamethod, v);
					}
					catch (UnresolvedControlThrowable ct) {
						throw ct.resolve(this, new SuspendedState(fmt, bld.toString(), args, index, width, flags, precision));
					}
					resume_s(context, bld, width, flags, precision);
					return;
				}
				else {
					s = Conversions.toHumanReadableString(v).toString();
				}
			}
			bld.append(justified(width, flags, trimmed(precision, s)));
		}

		private static void resume_s(ExecutionContext context, StringBuilder bld, int width, int flags, int precision) {
			Object o = context.getReturnBuffer().get0();
			ByteString sv = Conversions.stringValueOf(o);
			String s = sv != null ? sv.toString() : "";
			bld.append(justified(width, flags, trimmed(precision, s)));
		}

		private void format_q(StringBuilder bld, ArgumentIterator args) {
			Object o = args.nextAny();
			final String s;

			if (o == null) s = LuaFormat.NIL.toString();
			else if (o instanceof Boolean) s = LuaFormat.toString(((Boolean) o).booleanValue());
			else if (o instanceof String) s = LuaFormat.escape((String) o);
			else if (o instanceof ByteString) s = LuaFormat.escape(((ByteString) o).toString());
			else if (o instanceof Number) s = Conversions.stringValueOf((Number) o).toString();
			else {
				throw new BadArgumentException(args.at(), name(), "value has no literal form");
			}

			bld.append(s);
		}

		private int placeholder(ExecutionContext context, String fmt, int from, StringBuilder bld, ArgumentIterator args)
				throws ResolvedControlThrowable {

			if (!args.hasNext()) {
				throw new BadArgumentException(args.size() + 1, name(), "no value");
			}

			int index = from;

			char c;

			int flags = 0;

			// flags
			{
				boolean wasFlag = true;

				do {
					if (index < fmt.length()) {
						c = fmt.charAt(index++);
					}
					else {
						throw invalidOptionException('\0');
					}

					switch (c) {
						case '-': flags = setFlag(flags, FLAG_LEFTJUSTIFY); break;
						case '+': flags = setFlag(flags, FLAG_SIGN_ALWAYS); break;
						case ' ': flags = setFlag(flags, FLAG_SIGN_SPACE); break;
						case '0': flags = setFlag(flags, FLAG_ZERO_PAD); break;
						case '#': flags = setFlag(flags, FLAG_ALT_FORM); break;

						default:
							// not a flag, take the character back
							index -= 1;
							wasFlag = false;
							break;
					}

				} while (wasFlag);
			}

			// width
			int width = -1;

			{
				boolean wasWidth = true;

				do {
					if (index < fmt.length()) {
						c = fmt.charAt(index++);
					}
					else {
						throw invalidOptionException('\0');
					}

					if (c >= '0' && c <= '9') {
						width = Math.max(0, width) * 10 + (c - '0');
						if (width >= 100) {
							throw new IllegalArgumentException("illegal format (width or precision too long)");
						}
					}
					else {
						// not a width specifier, put back
						index -= 1;
						wasWidth = false;
					}

				} while (wasWidth);
			}

			// precision
			int precision = -1;

			{
				if (index < fmt.length() && fmt.charAt(index) == '.') {
					index += 1;  // skip the '.'
					precision = 0;

					boolean wasPrecision = true;
					do {
						if (index < fmt.length()) {
							c = fmt.charAt(index++);
						}
						else {
							throw invalidOptionException('\0');
						}

						if (c >= '0' && c <= '9') {
							precision = precision * 10 + (c - '0');
							if (precision >= 100) {
								throw new IllegalArgumentException("illegal format (width or precision too long)");
							}
						}
						else {
							// not a width specifier, put back
							index -= 1;
							wasPrecision = false;
						}

					} while (wasPrecision);

				}
			}

			// type
			{
				char d = fmt.charAt(index++);

				switch (d) {

					case 'd':
					case 'i':
						format_signed_integer(bld, args, d, width, flags, precision);
						break;

					case 'u':
						format_unsigned_integer(bld, args, width, flags, precision);
						break;

					case 'o':
						format_octal_integer(bld, args, width, flags, precision);
						break;

					case 'x':
					case 'X':
						format_hex_integer(bld, args, d == 'X', width, flags, precision);
						break;

					case 'c':
						format_char(bld, args, width, flags);
						break;

					case 'f':
					case 'a':
					case 'A':
					case 'e':
					case 'E':
					case 'g':
					case 'G':
						format_float(bld, args, d, width, flags, precision);
						break;

					case 's':
						format_s(context, fmt, bld, args, index, width, flags, precision);
						break;

					case 'q':
						format_q(bld, args);
						break;

					default:
						throw new IllegalArgumentException("invalid option '" + optionToString(d) + "' to 'format'");

				}

			}

			return index < fmt.length() ? index : -1;
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			String fmt = args.nextString().toString();  // FIXME
			StringBuilder bld = new StringBuilder();
			run(context, fmt, args, bld, 0);
		}

		@Override
		public void resume(ExecutionContext context, Object suspendedState) throws ResolvedControlThrowable {
			SuspendedState ss = (SuspendedState) suspendedState;

			StringBuilder bld = new StringBuilder(ss.str);

			// resume the suspended %s
			resume_s(context, bld, ss.width, ss.flags, ss.precision);

			// continue the loop
			run(context, ss.fmt, ss.args, bld, ss.index);
		}

		private void run(ExecutionContext context, String fmt, ArgumentIterator args, StringBuilder bld, int idx)
				throws ResolvedControlThrowable {
			do {
				idx = literal(fmt, idx, bld);
				if (idx >= 0) {
					idx = placeholder(context, fmt, idx, bld, args);
				}
			} while (idx >= 0);

			context.getReturnBuffer().setTo(bld.toString());
		}

	}

	public static class GMatch extends AbstractLibFunction {

		public static final GMatch INSTANCE = new GMatch();

		public static class IteratorFunction extends AbstractFunction0 {

			public final String string;
			public final StringPattern pattern;
			private final AtomicInteger index;

			public IteratorFunction(String string, StringPattern pattern) {
				this.string = Check.notNull(string);
				this.pattern = Check.notNull(pattern);
				this.index = new AtomicInteger(0);
			}

			@Override
			public void invoke(ExecutionContext context) throws ResolvedControlThrowable {

				int idx = index.get();

				if (idx >= 0) {
					StringPattern.Match m = pattern.match(string, idx);

					if (m != null) {
						// found a match
						int endIndex = m.endIndex();
						if (endIndex == idx) {
							// avoid looping on empty matches
							endIndex += 1;
						}

						index.set(endIndex);

						if (!m.captures().isEmpty()) {
							context.getReturnBuffer().setToContentsOf(m.captures());
						}
						else {
							context.getReturnBuffer().setTo(m.fullMatch());
						}
					}
					else {
						// no match; go to end state
						index.set(-1);
						context.getReturnBuffer().setTo();
					}
				}
				else {
					// in end state
					context.getReturnBuffer().setTo();
				}
			}

			@Override
			public void resume(ExecutionContext context, Object suspendedState) throws ResolvedControlThrowable {
				throw new NonsuspendableFunctionException(this.getClass());
			}
		}

		@Override
		protected String name() {
			return "gmatch";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			String s = args.nextString().toString();  // FIXME
			String pattern = args.nextString().toString();  // FIXME

			StringPattern pat = StringPattern.fromString(pattern, true);

			LuaFunction f = new IteratorFunction(s, pat);

			context.getReturnBuffer().setTo(f);
		}

	}

	public static class GSub extends AbstractLibFunction {

		public static final GSub INSTANCE = new GSub();

		private static final String ARG3_ERROR_MESSAGE = "string/function/table expected";

		@Override
		protected String name() {
			return "gsub";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			String s = args.nextString().toString();  // FIXME
			String pattern = args.nextString().toString();  // FIXME

			final Object repl;
			if (!args.hasNext()) {
				throw args.badArgument(3, ARG3_ERROR_MESSAGE);
			}
			else {
				Object o = args.nextAny();

				// a string?
				ByteString replStr = Conversions.stringValueOf(o);
				if (replStr != null) {
					repl = replStr.toString();
				}
				else if (o instanceof Table || o instanceof LuaFunction) {
					repl = o;
				}
				else {
					throw args.badArgument(3, ARG3_ERROR_MESSAGE);
				}
			}

			int n = args.optNextInt(Integer.MAX_VALUE);

			StringPattern pat = StringPattern.fromString(pattern);

			run(context, s, 0, new StringBuilder(), pat, 0, n, repl);
		}

		private static class State {

			public final String str;
			public final StringPattern pat;
			public final int count;
			public final int num;

			public final Object repl;

			public final StringBuilder bld;
			public final String fullMatch;
			public final int idx;

			private State(String str, StringPattern pat, int count, int num, Object repl, StringBuilder bld, String fullMatch, int idx) {
				this.str = str;
				this.pat = pat;
				this.count = count;
				this.num = num;
				this.repl = repl;
				this.bld = bld;
				this.fullMatch = fullMatch;
				this.idx = idx;
			}

		}

		private void run(ExecutionContext context, String str, int idx, StringBuilder bld, StringPattern pat, int count, int num, Object repl)
				throws ResolvedControlThrowable {

			while (count < num) {
				StringPattern.Match m = pat.match(str, idx);

				if (m == null) {
					// no more matches
					break;
				}

				count += 1;

				// non-matching prefix
				if (idx < m.beginIndex()) {
					bld.append(str.substring(idx, m.beginIndex()));
				}

				List<Object> captures = m.captures().isEmpty()
						? Collections.singletonList((Object) m.fullMatch())
						: m.captures();

				// avoid looping indefinitely for empty matches
				idx = m.endIndex() != idx ? m.endIndex() : m.endIndex() + 1;

				if (repl instanceof String) {
					String r = stringReplace((String) repl, m.fullMatch(), captures);
					bld.append(r);
				}
				else {
					// NOTE: throws and handles ControlThrowables
					nonStringReplace(
							context, str, pat, idx, count, num, bld,
							repl, m.fullMatch(), captures);
				}
			}

			// non-matching suffix
			if (idx < str.length()) {
				bld.append(str.substring(idx, str.length()));
			}

			context.getReturnBuffer().setTo(bld.toString(), (long) count);
		}

		private static String stringReplace(String s, String fullMatch, List<Object> captures) {
			StringBuilder bld = new StringBuilder();

			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);

				if (c == '%' && i + 1 < s.length()) {
					char d = s.charAt(i + 1);
					i += 1;  // skip the escape

					if (d >= '0' && d <= '9') {
						int idx = (int) d - (int) '0';
						if (idx == 0) {
							bld.append(fullMatch);
						}
						else {
							if (idx - 1 < captures.size()) {
								// captures are either strings or integers
								ByteString sv = Conversions.stringValueOf(captures.get(idx - 1));
								assert (sv != null);
								bld.append(sv);
							}
							else {
								// no capture with this index
								bld.append(d);
							}
						}
					}
					else {
						bld.append(d);
					}
				}
				else {
					bld.append(c);
				}
			}

			return bld.toString();
		}

		private void nonStringReplace(
				ExecutionContext context,
				String str,
				StringPattern pat,
				int idx,
				int count,
				int num,
				StringBuilder bld,
				Object repl,
				String fullMatch,
				List<Object> captures)
				throws ResolvedControlThrowable {

			assert (!captures.isEmpty());

			Object cap = captures.get(0);

			try {
				if (repl instanceof Table) {
					Dispatch.index(context, (Table) repl, cap);
				}
				else if (repl instanceof LuaFunction) {
					Dispatch.call(context, (LuaFunction) repl, (Object[]) captures.toArray());
				}
				else {
					throw new IllegalStateException("Illegal replacement: " + repl);
				}
			}
			catch (UnresolvedControlThrowable ct) {
				throw ct.resolve(this, new State(str, pat, count, num, repl, bld, fullMatch, idx));
			}
			resumeReplace(context, bld, fullMatch);
		}

		private static void resumeReplace(ExecutionContext context, StringBuilder bld, String fullMatch) {
			Object value = context.getReturnBuffer().get0();
			ByteString sv = Conversions.stringValueOf(value);
			if (sv != null) {
				bld.append(sv);
			}
			else {
				if (!Conversions.booleanValueOf(value)) {
					// false or nil
	                bld.append(fullMatch);
				}
				else {
					throw new LuaRuntimeException("invalid replacement value (a "
							+ PlainValueTypeNamer.INSTANCE.typeNameOf(value) + ")");
				}
			}
		}


		@Override
		public void resume(ExecutionContext context, Object suspendedState) throws ResolvedControlThrowable {
			State state = (State) suspendedState;
			resumeReplace(context, state.bld, state.fullMatch);
			run(context, state.str, state.idx, state.bld, state.pat, state.count, state.num, state.repl);
		}

	}

	public static class Len extends AbstractLibFunction {

		public static final Len INSTANCE = new Len();

		@Override
		protected String name() {
			return "len";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			ByteString s = args.nextString();
			context.getReturnBuffer().setTo((long) s.length());
		}

	}

	public static class Lower extends AbstractLibFunction {

		public static final Lower INSTANCE = new Lower();

		@Override
		protected String name() {
			return "lower";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			ByteString s = args.nextString();
			context.getReturnBuffer().setTo(toLowerCase(s));
		}

	}

	public static class Match extends AbstractLibFunction {

		public static final Match INSTANCE = new Match();

		@Override
		protected String name() {
			return "match";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			String s = args.nextString().toString();  // FIXME
			String pattern = args.nextString().toString();  // FIXME
			int init = args.optNextInt(1);

			init = lowerBound(init, s.length());

			StringPattern pat = StringPattern.fromString(pattern);

			StringPattern.Match m = pat.match(s, init - 1);
			if (m != null) {
				if (m.captures().isEmpty()) {
					context.getReturnBuffer().setTo(m.fullMatch());
				}
				else {
					context.getReturnBuffer().setToContentsOf(m.captures());
				}
			}
			else {
				// no match
				context.getReturnBuffer().setTo(null);
			}
		}

	}

	public static class Rep extends AbstractLibFunction {

		public static final Rep INSTANCE = new Rep();

		@Override
		protected String name() {
			return "rep";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			ByteString s = args.nextString();
			int n = args.nextInt();
			ByteString sep = args.hasNext() ? args.nextString() : ByteString.empty();

			final ByteString result;
			if (n > 0) {
				ByteStringBuilder bld = new ByteStringBuilder();

				for (int i = 0; i < n; i++) {
					bld.append(s);
					if (i + 1 < n) {
						bld.append(sep);
					}
				}

				result = bld.toByteString();
			}
			else {
				result = ByteString.empty();
			}

			context.getReturnBuffer().setTo(result);
		}

	}

	public static class Reverse extends AbstractLibFunction {

		public static final Reverse INSTANCE = new Reverse();

		@Override
		protected String name() {
			return "reverse";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			ByteString s = args.nextString();

			byte[] bytes = s.getBytes();
			for (int i = 0; i < bytes.length / 2; i++) {
				int j = bytes.length - 1 - i;

				byte tmp = bytes[i];
				bytes[i] = bytes[j];
				bytes[j] = tmp;
			}

			ByteString result = ByteString.copyOf(bytes);

			context.getReturnBuffer().setTo(result);
		}

	}

	public static class Sub extends AbstractLibFunction {

		public static final Sub INSTANCE = new Sub();

		@Override
		protected String name() {
			return "sub";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			ByteString s = args.nextString();
			int i = args.nextInt();
			int j = args.optNextInt(-1);

			int len = s.length();
			i = lowerBound(i, len) - 1;
			j = upperBound(j, len);

			ByteString result = s.substring(i, j);

			context.getReturnBuffer().setTo(result);
		}

	}

	public static class Upper extends AbstractLibFunction {

		public static final Upper INSTANCE = new Upper();

		@Override
		protected String name() {
			return "upper";
		}

		@Override
		protected void invoke(ExecutionContext context, ArgumentIterator args) throws ResolvedControlThrowable {
			ByteString s = args.nextString();
			context.getReturnBuffer().setTo(toUpperCase(s));
		}

	}

}
