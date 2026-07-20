package fr.luteal.core.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class LutealDatabase_Impl extends LutealDatabase {
  private volatile CycleDao _cycleDao;

  private volatile SymptomDao _symptomDao;

  private volatile UserProfileDao _userProfileDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `cycles` (`id` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `periodDaysJson` TEXT NOT NULL, `averageLengthDays` INTEGER NOT NULL, `lutealPhaseLengthDays` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `symptom_logs` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `timestampEpochMillis` INTEGER NOT NULL, `symptomId` TEXT NOT NULL, `severity` INTEGER NOT NULL, `notes` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `disorder_configs` (`disorderId` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `customNotes` TEXT NOT NULL, `alertPhaseDaysBefore` INTEGER NOT NULL, PRIMARY KEY(`disorderId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`userId` TEXT NOT NULL, `role` TEXT NOT NULL, `syncMode` TEXT NOT NULL, `couplePairingCode` TEXT, `partnerName` TEXT, `isPaired` INTEGER NOT NULL, PRIMARY KEY(`userId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'cb745e74fcffa821629569e82e7b8c34')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `cycles`");
        db.execSQL("DROP TABLE IF EXISTS `symptom_logs`");
        db.execSQL("DROP TABLE IF EXISTS `disorder_configs`");
        db.execSQL("DROP TABLE IF EXISTS `user_profiles`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsCycles = new HashMap<String, TableInfo.Column>(7);
        _columnsCycles.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCycles.put("startDate", new TableInfo.Column("startDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCycles.put("endDate", new TableInfo.Column("endDate", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCycles.put("periodDaysJson", new TableInfo.Column("periodDaysJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCycles.put("averageLengthDays", new TableInfo.Column("averageLengthDays", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCycles.put("lutealPhaseLengthDays", new TableInfo.Column("lutealPhaseLengthDays", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCycles.put("isSynced", new TableInfo.Column("isSynced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCycles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCycles = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCycles = new TableInfo("cycles", _columnsCycles, _foreignKeysCycles, _indicesCycles);
        final TableInfo _existingCycles = TableInfo.read(db, "cycles");
        if (!_infoCycles.equals(_existingCycles)) {
          return new RoomOpenHelper.ValidationResult(false, "cycles(fr.luteal.core.data.entity.CycleEntity).\n"
                  + " Expected:\n" + _infoCycles + "\n"
                  + " Found:\n" + _existingCycles);
        }
        final HashMap<String, TableInfo.Column> _columnsSymptomLogs = new HashMap<String, TableInfo.Column>(7);
        _columnsSymptomLogs.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSymptomLogs.put("date", new TableInfo.Column("date", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSymptomLogs.put("timestampEpochMillis", new TableInfo.Column("timestampEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSymptomLogs.put("symptomId", new TableInfo.Column("symptomId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSymptomLogs.put("severity", new TableInfo.Column("severity", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSymptomLogs.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSymptomLogs.put("isSynced", new TableInfo.Column("isSynced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSymptomLogs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSymptomLogs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSymptomLogs = new TableInfo("symptom_logs", _columnsSymptomLogs, _foreignKeysSymptomLogs, _indicesSymptomLogs);
        final TableInfo _existingSymptomLogs = TableInfo.read(db, "symptom_logs");
        if (!_infoSymptomLogs.equals(_existingSymptomLogs)) {
          return new RoomOpenHelper.ValidationResult(false, "symptom_logs(fr.luteal.core.data.entity.SymptomLogEntity).\n"
                  + " Expected:\n" + _infoSymptomLogs + "\n"
                  + " Found:\n" + _existingSymptomLogs);
        }
        final HashMap<String, TableInfo.Column> _columnsDisorderConfigs = new HashMap<String, TableInfo.Column>(4);
        _columnsDisorderConfigs.put("disorderId", new TableInfo.Column("disorderId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDisorderConfigs.put("isEnabled", new TableInfo.Column("isEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDisorderConfigs.put("customNotes", new TableInfo.Column("customNotes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDisorderConfigs.put("alertPhaseDaysBefore", new TableInfo.Column("alertPhaseDaysBefore", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDisorderConfigs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDisorderConfigs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDisorderConfigs = new TableInfo("disorder_configs", _columnsDisorderConfigs, _foreignKeysDisorderConfigs, _indicesDisorderConfigs);
        final TableInfo _existingDisorderConfigs = TableInfo.read(db, "disorder_configs");
        if (!_infoDisorderConfigs.equals(_existingDisorderConfigs)) {
          return new RoomOpenHelper.ValidationResult(false, "disorder_configs(fr.luteal.core.data.entity.DisorderConfigEntity).\n"
                  + " Expected:\n" + _infoDisorderConfigs + "\n"
                  + " Found:\n" + _existingDisorderConfigs);
        }
        final HashMap<String, TableInfo.Column> _columnsUserProfiles = new HashMap<String, TableInfo.Column>(6);
        _columnsUserProfiles.put("userId", new TableInfo.Column("userId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfiles.put("role", new TableInfo.Column("role", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfiles.put("syncMode", new TableInfo.Column("syncMode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfiles.put("couplePairingCode", new TableInfo.Column("couplePairingCode", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfiles.put("partnerName", new TableInfo.Column("partnerName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfiles.put("isPaired", new TableInfo.Column("isPaired", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserProfiles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserProfiles = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUserProfiles = new TableInfo("user_profiles", _columnsUserProfiles, _foreignKeysUserProfiles, _indicesUserProfiles);
        final TableInfo _existingUserProfiles = TableInfo.read(db, "user_profiles");
        if (!_infoUserProfiles.equals(_existingUserProfiles)) {
          return new RoomOpenHelper.ValidationResult(false, "user_profiles(fr.luteal.core.data.entity.UserProfileEntity).\n"
                  + " Expected:\n" + _infoUserProfiles + "\n"
                  + " Found:\n" + _existingUserProfiles);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "cb745e74fcffa821629569e82e7b8c34", "2ac870d04f2aba79e456d26db492185d");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "cycles","symptom_logs","disorder_configs","user_profiles");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `cycles`");
      _db.execSQL("DELETE FROM `symptom_logs`");
      _db.execSQL("DELETE FROM `disorder_configs`");
      _db.execSQL("DELETE FROM `user_profiles`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(CycleDao.class, CycleDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SymptomDao.class, SymptomDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UserProfileDao.class, UserProfileDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public CycleDao cycleDao() {
    if (_cycleDao != null) {
      return _cycleDao;
    } else {
      synchronized(this) {
        if(_cycleDao == null) {
          _cycleDao = new CycleDao_Impl(this);
        }
        return _cycleDao;
      }
    }
  }

  @Override
  public SymptomDao symptomDao() {
    if (_symptomDao != null) {
      return _symptomDao;
    } else {
      synchronized(this) {
        if(_symptomDao == null) {
          _symptomDao = new SymptomDao_Impl(this);
        }
        return _symptomDao;
      }
    }
  }

  @Override
  public UserProfileDao userProfileDao() {
    if (_userProfileDao != null) {
      return _userProfileDao;
    } else {
      synchronized(this) {
        if(_userProfileDao == null) {
          _userProfileDao = new UserProfileDao_Impl(this);
        }
        return _userProfileDao;
      }
    }
  }
}
