package protect.card_locker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import protect.card_locker.async.CompatCallable;

/**
 * This task will generate a barcode and load it into an ImageView.
 * Only a weak reference of the ImageView is kept, so this class will not
 * prevent the ImageView from being garbage collected.
 */
public class BarcodeImageWriterTask implements CompatCallable<Bitmap> {
    private static final String TAG = "Catima";

    private static final int IS_VALID = 999;
    private final Context mContext;
    private boolean isSuccesful;

    // When drawn in a smaller window 1D barcodes for some reason end up
    // squished, whereas 2D barcodes look fine.
    private static final int MAX_WIDTH_1D = 1500;
    private static final int MAX_WIDTH_2D = 500;

    private final WeakReference<ImageView> imageViewReference;
    private final WeakReference<TextView> textViewReference;
    private String cardId;
    private final CatimaBarcode format;
    private final int imageHeight;
    private final int imageWidth;
    private final BarcodeImageRenderer barcodeImageRenderer;
    private final int imagePadding;
    private final boolean widthPadding;
    private final boolean showFallback;
    private final BarcodeImageWriterResultCallback callback;

    BarcodeImageWriterTask(
            Context context, ImageView imageView, String cardIdString,
            CatimaBarcode barcodeFormat, TextView textView,
            boolean showFallback, BarcodeImageWriterResultCallback callback, boolean roundCornerPadding, boolean isFullscreen
    ) {
        mContext = context;

        isSuccesful = true;
        this.callback = callback;

        // Use a WeakReference to ensure the ImageView can be garbage collected
        imageViewReference = new WeakReference<>(imageView);
        textViewReference = new WeakReference<>(textView);

        cardId = cardIdString;
        format = barcodeFormat;

        int imageViewHeight = imageView.getHeight();
        int imageViewWidth = imageView.getWidth();

        // Some barcodes already have internal whitespace and shouldn't get extra padding
        // TODO: Get rid of this hack by somehow detecting this extra whitespace
        if (roundCornerPadding && !barcodeFormat.hasInternalPadding()) {
            imagePadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, context.getResources().getDisplayMetrics()));
        } else {
            imagePadding = 0;
        }

        if (format.isSquare() && imageViewWidth > imageViewHeight) {
            imageViewWidth -= imagePadding;
            widthPadding = true;
        } else {
            imageViewHeight -= imagePadding;
            widthPadding = false;
        }

        final int MAX_WIDTH = getMaxWidth(format);

        if (format.isSquare()) {
            imageHeight = imageWidth = Math.min(imageViewHeight, Math.min(MAX_WIDTH, imageViewWidth));
        } else if (imageView.getWidth() < MAX_WIDTH && !isFullscreen) {
            imageHeight = imageViewHeight;
            imageWidth = imageViewWidth;
        } else {
            // Scale down the image to reduce the memory needed to produce it
            imageWidth = Math.min(MAX_WIDTH, this.mContext.getResources().getDisplayMetrics().widthPixels);
            double ratio = (double) imageWidth / (double) imageViewWidth;
            imageHeight = (int) (imageViewHeight * ratio);
        }

        this.barcodeImageRenderer = new BarcodeImageRenderer(format, imageHeight, imageWidth);
        this.showFallback = showFallback;
    }

    private int getMaxWidth(CatimaBarcode format) {
        return switch (format.format()) {
            // 2D barcodes
            case AZTEC, MAXICODE, PDF_417, QR_CODE -> MAX_WIDTH_2D;

            // 2D but rectangular versions get blurry otherwise
            case DATA_MATRIX -> MAX_WIDTH_1D;

            // 1D barcodes:
            case CODABAR, CODE_39, CODE_93, CODE_128, EAN_8, EAN_13, ITF, UPC_A, UPC_E,
                 RSS_14, RSS_EXPANDED, UPC_EAN_EXTENSION -> MAX_WIDTH_1D;
        };
    }

    private String getFallbackString(CatimaBarcode format) {
        return switch (format.format()) {
            // 2D barcodes
            case AZTEC -> "AZTEC";
            case DATA_MATRIX -> "DATA_MATRIX";
            case PDF_417 -> "PDF_417";
            case QR_CODE -> "QR_CODE";

            // 1D barcodes:
            case CODABAR -> "C0C";
            case CODE_39 -> "CODE_39";
            case CODE_93 -> "CODE_93";
            case CODE_128 -> "CODE_128";
            case EAN_8 -> "32123456";
            case EAN_13 -> "5901234123457";
            case ITF -> "1003";
            case UPC_A -> "123456789012";
            case UPC_E -> "0123456";

            default -> throw new IllegalArgumentException("No fallback known for this barcode type");
        };
    }

    public Bitmap doInBackground(Void... params) {
        // Only do the hard tasks if we've not already been cancelled
        if (!Thread.currentThread().isInterrupted()) {
            Bitmap bitmap = barcodeImageRenderer.generate(cardId);

            if (bitmap == null) {
                isSuccesful = false;

                if (showFallback && !Thread.currentThread().isInterrupted()) {
                    Log.i(TAG, "Barcode generation failed, generating fallback...");
                    cardId = getFallbackString(format);
                    bitmap = barcodeImageRenderer.generate(cardId);
                    return bitmap;
                }
            } else {
                return bitmap;
            }
        }

        // We've been interrupted - create a empty fallback
        Bitmap.Config config = Bitmap.Config.ARGB_8888;
        return Bitmap.createBitmap(imageWidth, imageHeight, config);
    }

    public void onPostExecute(Object castResult) {
        Bitmap result = (Bitmap) castResult;

        Log.i(TAG, "Finished generating barcode image of type " + format + ": " + cardId);
        ImageView imageView = imageViewReference.get();
        if (imageView == null) {
            // The ImageView no longer exists, nothing to do
            return;
        }

        String formatPrettyName = format.prettyName();

        imageView.setTag(isSuccesful);

        imageView.setImageBitmap(result);
        imageView.setContentDescription(mContext.getString(R.string.barcodeImageDescriptionWithType, formatPrettyName));
        TextView textView = textViewReference.get();

        if (result != null) {
            Log.i(TAG, "Displaying barcode");
            if (widthPadding) {
                imageView.setPadding(imagePadding / 2, 0, imagePadding / 2, 0);
            } else {
                imageView.setPadding(0, imagePadding / 2, 0, imagePadding / 2);
            }
            imageView.setVisibility(View.VISIBLE);

            if (isSuccesful) {
                imageView.setColorFilter(null);
            } else {
                imageView.setColorFilter(Color.LTGRAY, PorterDuff.Mode.LIGHTEN);
            }

            if (textView != null) {
                textView.setVisibility(View.VISIBLE);
                textView.setText(formatPrettyName);
            }
        } else {
            Log.i(TAG, "Barcode generation failed, removing image from display");
            imageView.setVisibility(View.GONE);
            if (textView != null) {
                textView.setVisibility(View.GONE);
            }
        }

        if (callback != null) {
            callback.onBarcodeImageWriterResult(isSuccesful);
        }
    }

    @Override
    public void onPreExecute() {
        // No Action
    }

    /**
     * Provided to comply with Callable while keeping the original Syntax of AsyncTask
     *
     * @return generated Bitmap
     */
    @Override
    public Bitmap call() {
        return doInBackground();
    }
}
