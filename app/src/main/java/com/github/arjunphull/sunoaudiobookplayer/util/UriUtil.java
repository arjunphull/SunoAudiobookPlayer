package com.github.arjunphull.sunoaudiobookplayer.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.IOException;

public class UriUtil {
    public static int checkUriExists(Context context, Uri uri) {
        try {
            AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd == null) {
                return -1;
            } else {
                afd.close();
            }
        } catch (FileNotFoundException | IllegalArgumentException e) {
            return -1;
        } catch (SecurityException e) {
            return 0;
        }
        catch (IOException e) {
            // unable to close afd; do nothing
        }
        return 1;
    }
}
