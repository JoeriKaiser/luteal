package fr.luteal.core.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import fr.luteal.core.data.entity.CycleEntity;
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
public final class CycleDao_Impl implements CycleDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CycleEntity> __insertionAdapterOfCycleEntity;

  private final EntityDeletionOrUpdateAdapter<CycleEntity> __updateAdapterOfCycleEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteCycle;

  public CycleDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCycleEntity = new EntityInsertionAdapter<CycleEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `cycles` (`id`,`startDate`,`endDate`,`periodDaysJson`,`averageLengthDays`,`lutealPhaseLengthDays`,`isSynced`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CycleEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getStartDate());
        if (entity.getEndDate() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getEndDate());
        }
        statement.bindString(4, entity.getPeriodDaysJson());
        statement.bindLong(5, entity.getAverageLengthDays());
        statement.bindLong(6, entity.getLutealPhaseLengthDays());
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(7, _tmp);
      }
    };
    this.__updateAdapterOfCycleEntity = new EntityDeletionOrUpdateAdapter<CycleEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `cycles` SET `id` = ?,`startDate` = ?,`endDate` = ?,`periodDaysJson` = ?,`averageLengthDays` = ?,`lutealPhaseLengthDays` = ?,`isSynced` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CycleEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getStartDate());
        if (entity.getEndDate() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getEndDate());
        }
        statement.bindString(4, entity.getPeriodDaysJson());
        statement.bindLong(5, entity.getAverageLengthDays());
        statement.bindLong(6, entity.getLutealPhaseLengthDays());
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(7, _tmp);
        statement.bindString(8, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteCycle = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cycles WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertCycle(final CycleEntity cycle, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCycleEntity.insert(cycle);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateCycle(final CycleEntity cycle, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfCycleEntity.handle(cycle);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteCycle(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteCycle.acquire();
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
          __preparedStmtOfDeleteCycle.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CycleEntity>> getAllCycles() {
    final String _sql = "SELECT * FROM cycles ORDER BY startDate DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cycles"}, new Callable<List<CycleEntity>>() {
      @Override
      @NonNull
      public List<CycleEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfEndDate = CursorUtil.getColumnIndexOrThrow(_cursor, "endDate");
          final int _cursorIndexOfPeriodDaysJson = CursorUtil.getColumnIndexOrThrow(_cursor, "periodDaysJson");
          final int _cursorIndexOfAverageLengthDays = CursorUtil.getColumnIndexOrThrow(_cursor, "averageLengthDays");
          final int _cursorIndexOfLutealPhaseLengthDays = CursorUtil.getColumnIndexOrThrow(_cursor, "lutealPhaseLengthDays");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final List<CycleEntity> _result = new ArrayList<CycleEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CycleEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpStartDate;
            _tmpStartDate = _cursor.getString(_cursorIndexOfStartDate);
            final String _tmpEndDate;
            if (_cursor.isNull(_cursorIndexOfEndDate)) {
              _tmpEndDate = null;
            } else {
              _tmpEndDate = _cursor.getString(_cursorIndexOfEndDate);
            }
            final String _tmpPeriodDaysJson;
            _tmpPeriodDaysJson = _cursor.getString(_cursorIndexOfPeriodDaysJson);
            final int _tmpAverageLengthDays;
            _tmpAverageLengthDays = _cursor.getInt(_cursorIndexOfAverageLengthDays);
            final int _tmpLutealPhaseLengthDays;
            _tmpLutealPhaseLengthDays = _cursor.getInt(_cursorIndexOfLutealPhaseLengthDays);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _item = new CycleEntity(_tmpId,_tmpStartDate,_tmpEndDate,_tmpPeriodDaysJson,_tmpAverageLengthDays,_tmpLutealPhaseLengthDays,_tmpIsSynced);
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
  public Flow<CycleEntity> getCurrentCycle() {
    final String _sql = "SELECT * FROM cycles WHERE endDate IS NULL LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cycles"}, new Callable<CycleEntity>() {
      @Override
      @Nullable
      public CycleEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfEndDate = CursorUtil.getColumnIndexOrThrow(_cursor, "endDate");
          final int _cursorIndexOfPeriodDaysJson = CursorUtil.getColumnIndexOrThrow(_cursor, "periodDaysJson");
          final int _cursorIndexOfAverageLengthDays = CursorUtil.getColumnIndexOrThrow(_cursor, "averageLengthDays");
          final int _cursorIndexOfLutealPhaseLengthDays = CursorUtil.getColumnIndexOrThrow(_cursor, "lutealPhaseLengthDays");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "isSynced");
          final CycleEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpStartDate;
            _tmpStartDate = _cursor.getString(_cursorIndexOfStartDate);
            final String _tmpEndDate;
            if (_cursor.isNull(_cursorIndexOfEndDate)) {
              _tmpEndDate = null;
            } else {
              _tmpEndDate = _cursor.getString(_cursorIndexOfEndDate);
            }
            final String _tmpPeriodDaysJson;
            _tmpPeriodDaysJson = _cursor.getString(_cursorIndexOfPeriodDaysJson);
            final int _tmpAverageLengthDays;
            _tmpAverageLengthDays = _cursor.getInt(_cursorIndexOfAverageLengthDays);
            final int _tmpLutealPhaseLengthDays;
            _tmpLutealPhaseLengthDays = _cursor.getInt(_cursorIndexOfLutealPhaseLengthDays);
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            _result = new CycleEntity(_tmpId,_tmpStartDate,_tmpEndDate,_tmpPeriodDaysJson,_tmpAverageLengthDays,_tmpLutealPhaseLengthDays,_tmpIsSynced);
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
