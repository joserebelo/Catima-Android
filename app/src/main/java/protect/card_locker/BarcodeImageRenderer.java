package protect.card_locker;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

public class BarcodeImageRenderer {
    private static final String TAG = "Catima";

    private final CatimaBarcode format;
    private final int imageHeight;
    private final int imageWidth;

    public BarcodeImageRenderer(final CatimaBarcode format,
                                int imageHeight,
                                int imageWidth) {
        this.format = format;
        this.imageHeight = imageHeight;
        this.imageWidth = imageWidth;
    }

    public Bitmap generate(final String cardId) {
        if (cardId.isEmpty()) {
            return null;
        }

        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix bitMatrix;
        try {
            try {
                bitMatrix = writer.encode(cardId, format.format(), imageWidth, imageHeight, null);
            } catch (Exception e) {
                // Cast a wider net here and catch any exception, as there are some
                // cases where an encoder may fail if the data is invalid for the
                // barcode type. If this happens, we want to fail gracefully.
                throw new WriterException(e);
            }

            final int WHITE = 0xFFFFFFFF;
            final int BLACK = 0xFF000000;

            int bitMatrixWidth = bitMatrix.getWidth();
            int bitMatrixHeight = bitMatrix.getHeight();

            int[] pixels = new int[bitMatrixWidth * bitMatrixHeight];

            for (int y = 0; y < bitMatrixHeight; y++) {
                int offset = y * bitMatrixWidth;
                for (int x = 0; x < bitMatrixWidth; x++) {
                    int color = bitMatrix.get(x, y) ? BLACK : WHITE;
                    pixels[offset + x] = color;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(bitMatrixWidth, bitMatrixHeight,
                    Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, bitMatrixWidth, 0, 0, bitMatrixWidth, bitMatrixHeight);

            // Determine if the image needs to be scaled.
            // This is necessary because the datamatrix barcode generator
            // ignores the requested size and returns the smallest image necessary
            // to represent the barcode. If we let the ImageView scale the image
            // it will use bi-linear filtering, which results in a blurry barcode.
            // To avoid this, if scaling is needed do so without filtering.

            int heightScale = imageHeight / bitMatrixHeight;
            int widthScale = imageWidth / bitMatrixHeight;
            int scalingFactor = Math.min(heightScale, widthScale);

            if (scalingFactor > 1) {
                bitmap = Bitmap.createScaledBitmap(bitmap, bitMatrixWidth * scalingFactor, bitMatrixHeight * scalingFactor, false);
            }

            return bitmap;
        } catch (WriterException e) {
            Log.e(TAG, "Failed to generate barcode of type " + format + ": " + cardId, e);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "Insufficient memory to render barcode, "
                    + imageWidth + "x" + imageHeight + ", " + format.name()
                    + ", length=" + cardId.length(), e);
        }

        return null;
    }
}
