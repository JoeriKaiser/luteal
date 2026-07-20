package fr.luteal.core.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import fr.luteal.core.data.entity.SymptomLogEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SymptomDao_Impl implements SymptomDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SymptomLogEntity> __insertionAdapterOfSymptomLogEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSymptomLog;

  public SymptomDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSymptomLogEntity = new EntityInsertionAdapter<SymptomLogEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `symptom_logs` (`id`,`date`,`timestampEpochMillis`,`symptomId`,`severity`,`notes`,`isSynced`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SymptomLogEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getDate());
        statement.bindLong(3, entity.getTimestampEpochMillis());
        statement.bindString(4, entity.getSymptomId());
        statement.bindLong(5, entity.getSeverity());
        statement.bindString(6, entity.getNotes());
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(7, _tmp);
      }
    };
    this.__preparedStmtOfDeleteSymptomLog = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM symptom_logs WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertSymptomLog(final SymptomLogEntity log,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSymptomLogEntity.insert(log);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSymptomLog(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSymptomLog.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteSymptomLog.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<SymptomLogEntity>> getSymptomsForDate(final String dateString) {
    final String _sql = "SELECT * FROM symptom_logs WHERE date = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, dateString);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"symptom_logs"}, new Callable<List<SymptomLogEntity>>() {
      @Override
      @NonNull
      public List<SymptomLogEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfTimestampEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampEpochMillis");
          final int _cursorIndexOfSymptomId = CursorUtil.getColumnIndexOrThrow(_cursor, "symptomId");
          final int _cursorIndexOfSeverity = CursorUtil.getColumnIndexOrThrow(_cursor, "severity");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<SymptomLogEntity> _result = new ArrayList<SymptomLogEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SymptomLogEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpDate;
            _tmpDate = _cursor.getString(_cursorIndexOfDate);
            final long _tmpTimestampEpochMillis;
            _tmpTimestampEpochMillis = _cursor.getLong(_cursorIndexOfTimestampEpochMillis);
            final String _tmpSymptomId;
            _tmpSymptomId = _cursor.getString(_cursorIndexOfSymptomId);
            final int _tmpSeverity;
            _tmpSeverity = _cursor.getInt(_cursorIndexOfSeverity);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new SymptomLogEntity(_tmpId,_tmpDate,_tmpTimestampEpochMillis,_tmpSymptomId,_tmpSeverity,_tmpNotes,_tmpIsSynced);
            _result.add(_item);
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

  @Override
  public Flow<List<SymptomLogEntity>> getAllSymptomLogs() {
    final String _sql = "SELECT * FROM symptom_logs ORDER BY timestampEpochMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"symptom_logs"}, new Callable<List<SymptomLogEntity>>() {
      @Override
      @NonNull
      public List<SymptomLogEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfTimestampEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampEpochMillis");
          final int _cursorIndexOfSymptomId = CursorUtil.getColumnIndexOrThrow(_cursor, "symptomId");
          final int _cursorIndexOfSeverity = CursorUtil.getColumnIndexOrThrow(_cursor, "severity");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<SymptomLogEntity> _result = new ArrayList<SymptomLogEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SymptomLogEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpDate;
            _tmpDate = _cursor.getString(_cursorIndexOfDate);
            final long _tmpTimestampEpochMillis;
            _tmpTimestampEpochMillis = _cursor.getLong(_cursorIndexOfTimestampEpochMillis);
            final String _tmpSymptomId;
            _tmpSymptomId = _cursor.getString(_cursorIndexOfSymptomId);
            final int _tmpSeverity;
            _tmpSeverity = _cursor.getInt(_cursorIndexOfSeverity);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new SymptomLogEntity(_tmpId,_tmpDate,_tmpTimestampEpochMillis,_tmpSymptomId,_tmpSeverity,_tmpNotes,_tmpIsSynced);
            _result.add(_item);
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
