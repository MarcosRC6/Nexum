package com.example.testario.ui.screen

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageDisplayGeometryTest {
    @Test fun portraitExifQuarterTurnsSwapDisplayedBounds() {
        assertEquals(
            600 to 1200,
            ImageDisplayGeometry.orientedDimensions(
                1200, 600, ExifInterface.ORIENTATION_ROTATE_90
            )
        )
        assertEquals(
            600 to 1200,
            ImageDisplayGeometry.orientedDimensions(
                1200, 600, ExifInterface.ORIENTATION_ROTATE_270
            )
        )
        assertEquals(
            600 to 1200,
            ImageDisplayGeometry.orientedDimensions(
                1200, 600, ExifInterface.ORIENTATION_TRANSVERSE
            )
        )
    }

    @Test fun normalFlipAndHalfTurnPreserveAspectRatio() {
        listOf(
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_ROTATE_180
        ).forEach {
            assertEquals(1200 to 800, ImageDisplayGeometry.orientedDimensions(1200, 800, it))
        }
    }
}
