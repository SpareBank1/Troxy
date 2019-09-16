package no.sb1.troxy.common;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A String compressed using the ZLIB algorithm, used to save memory for exceptionally large Strings.
 * If the String is below a certain length no compression is done.
 */
public class CompressedString {
    private static final Logger log = LoggerFactory.getLogger(CompressedString.class);
    private static final int MIN_COMPRESS_SIZE = 32768;
    private final CharSequence text;

    public CompressedString(String text) {
        this.text = text == null || text.length() < MIN_COMPRESS_SIZE ? text : new ZlibString(text);
    }

    @Override
    public String toString() {
        return text.toString();
    }

    private class ZlibString implements CharSequence {
        private static final String ENCODE_CHARSET = "ISO-8859-1";  // charset doesn't matter as long as it's inflated using the same charset, iso-8859-1 is probably faster than utf-8
        private int inflateSize;
        private byte[] data;

        public ZlibString(String text) {
            try {
                Deflater deflater = new Deflater();
                byte[] tmp = text.getBytes(ENCODE_CHARSET);
                inflateSize = tmp.length;
                deflater.setInput(tmp);
                deflater.finish();
                tmp = new byte[MIN_COMPRESS_SIZE];
                ByteArrayOutputStream baos = new ByteArrayOutputStream(MIN_COMPRESS_SIZE);
                while (true) {
                    int written = deflater.deflate(tmp);
                    if (written > 0)
                        baos.write(tmp, 0, written);
                    else
                        break;
                }
                deflater.end();
                data = baos.toByteArray();
                log.info("Compressed text from {} bytes to {} ({}%)", inflateSize, data.length, data.length * 100 / inflateSize);
            } catch (UnsupportedEncodingException e) {
                log.error("Unable to compress string", e);
            }
        }

        @Override
        public int length() {
            return toString().length();
        }

        @Override
        public char charAt(int index) {
            return toString().charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return toString().subSequence(start, end);
        }

        @Override
        public String toString() {
            try {
                Inflater inflater = new Inflater();
                inflater.setInput(data);
                byte[] tmp = new byte[inflateSize];
                inflater.inflate(tmp);
                inflater.end();
                return new String(tmp, ENCODE_CHARSET);
            } catch (UnsupportedEncodingException | DataFormatException e) {
                log.error("Unable to compress string", e);
            }
            return "";
        }
    }
}
