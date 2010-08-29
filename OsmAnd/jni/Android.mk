LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)


LOCAL_LDLIBS := -ldl
LOCAL_MODULE := sqlitejdbc
LOCAL_CFLAGS += -DHAVE_USLEEP=1 -DSQLITE_DEFAULT_JOURNAL_SIZE_LIMIT=1048576 -DSQLITE_THREADSAFE=1 \
			    -DNDEBUG=1 -DSQLITE_ENABLE_MEMORY_MANAGEMENT=1 -DSQLITE_DEFAULT_AUTOVACUUM=1 \
			    -DSQLITE_ENABLE_RTREE=1 -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_ENABLE_FTS3
			     
LOCAL_SRC_FILES := NativeDB.c sqlite3.c
    	
           	
include $(BUILD_SHARED_LIBRARY)




