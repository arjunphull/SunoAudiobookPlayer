package com.github.arjunphull.sunoaudiobookplayer.util;

import java.util.Collection;

public class CollectionUtil {
    public static boolean isCollectionEmpty_Synchronized(Collection collection) {
        synchronized (collection) {
            return collection.isEmpty();
        }
    }
}
