package fr.luteal.core.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import fr.luteal.core.data.entity.UserProfileEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class UserProfileDao_Impl implements UserProfileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<UserProfileEntity> __insertionAdapterOfUserProfileEntity;

  public UserProfileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfUserProfileEntity = new EntityInsertionAdapter<UserProfileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `user_profiles` (`userId`,`role`,`syncMode`,`couplePairingCode`,`partnerName`,`isPaired`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserProfileEntity entity) {
        statement.bindString(1, entity.getUserId());
        statement.bindString(2, entity.getRole());
        statement.bindString(3, entity.getSyncMode());
        if (entity.getCouplePairingCode() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getCouplePairingCode());
        }
        if (entity.getPartnerName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getPartnerName());
        }
        final int _tmp = entity.isPaired() ? 1 : 0;
        statement.bindLong(6, _tmp);
      }
    };
  }

  @Override
  public Object insertOrUpdateUserProfile(final UserProfileEntity profile,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfUserProfileEntity.insert(profile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<UserProfileEntity> getUserProfile() {
    final String _sql = "SELECT * FROM user_profiles LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"user_profiles"}, new Callable<UserProfileEntity>() {
      @Override
      @Nullable
      public UserProfileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfRole = CursorUtil.getColumnIndexOrThrow(_cursor, "role");
          final int _cursorIndexOfSyncMode = CursorUtil.getColumnIndexOrThrow(_cursor, "syncMode");
          final int _cursorIndexOfCouplePairingCode = CursorUtil.getColumnIndexOrThrow(_cursor, "couplePairingCode");
          final int _cursorIndexOfPartnerName = CursorUtil.getColumnIndexOrThrow(_cursor, "partnerName");
          final int _cursorIndexOfIsPaired = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaired");
          final UserProfileEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpRole;
            _tmpRole = _cursor.getString(_cursorIndexOfRole);
            final String _tmpSyncMode;
            _tmpSyncMode = _cursor.getString(_cursorIndexOfSyncMode);
            final String _tmpCouplePairingCode;
            if (_cursor.isNull(_cursorIndexOfCouplePairingCode)) {
              _tmpCouplePairingCode = null;
            } else {
              _tmpCouplePairingCode = _cursor.getString(_cursorIndexOfCouplePairingCode);
            }
            final String _tmpPartnerName;
            if (_cursor.isNull(_cursorIndexOfPartnerName)) {
              _tmpPartnerName = null;
            } else {
              _tmpPartnerName = _cursor.getString(_cursorIndexOfPartnerName);
            }
            final boolean _tmpIsPaired;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPaired);
            _tmpIsPaired = _tmp != 0;
            _result = new UserProfileEntity(_tmpUserId,_tmpRole,_tmpSyncMode,_tmpCouplePairingCode,_tmpPartnerName,_tmpIsPaired);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
