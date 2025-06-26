# Temporary Network Data Persistence Implementation

## Overview
This implementation fixes the issue where user-added images, comments, and passwords disappear after scan refreshes when networks are not pinned. The solution uses a local database to persist temporary network data linked to each network's BSSID.

## Key Components

### 1. Database Layer

#### TemporaryNetworkData Entity
- **File**: `app/src/main/java/com/ner/wimap/data/database/TemporaryNetworkData.kt`
- **Purpose**: Room entity to store temporary network data
- **Key Fields**:
  - `bssid` (Primary Key): Unique identifier for the network
  - `ssid`: Network name
  - `comment`: User-added comment
  - `savedPassword`: User-entered password
  - `photoPath`: Path to attached image
  - `isPinned`: Pin status
  - `lastUpdated`: Timestamp for cleanup purposes

#### TemporaryNetworkDataDao
- **File**: `app/src/main/java/com/ner/wimap/data/database/TemporaryNetworkDataDao.kt`
- **Purpose**: Data Access Object for database operations
- **Key Methods**:
  - `getTemporaryDataByBssid()`: Retrieve data for specific network
  - `insertOrUpdateTemporaryData()`: Save/update network data
  - `getAllTemporaryData()`: Get all temporary data (for reactive updates)
  - `deleteOldTemporaryData()`: Cleanup old entries

### 2. Repository Layer

#### TemporaryNetworkDataRepository
- **Interface**: `app/src/main/java/com/ner/wimap/domain/repository/TemporaryNetworkDataRepository.kt`
- **Implementation**: `app/src/main/java/com/ner/wimap/data/repository/TemporaryNetworkDataRepositoryImpl.kt`
- **Purpose**: Abstraction layer for temporary data operations

### 3. Use Case Layer

#### ManageTemporaryNetworkDataUseCase
- **File**: `app/src/main/java/com/ner/wimap/domain/usecase/ManageTemporaryNetworkDataUseCase.kt`
- **Purpose**: Business logic for managing temporary network data
- **Key Methods**:
  - `saveOrUpdateTemporaryNetworkData()`: Save data for any network
  - `enrichNetworkWithTemporaryData()`: Merge temporary data with network objects
  - `cleanupOldTemporaryData()`: Remove stale data

### 4. ViewModel Integration

#### MainViewModel Updates
- **File**: `app/src/main/java/com/ner/wimap/presentation/viewmodel/MainViewModel.kt`
- **Key Changes**:
  - Added `ManageTemporaryNetworkDataUseCase` dependency
  - Created `enhancedNetworks` flow that combines raw networks with temporary data
  - Added `updateTemporaryNetworkData()` method for saving user input
  - Added `pinNetworkWithTemporaryData()` for unified pin/unpin operations
  - Updated `clearAllData()` to include temporary data cleanup

### 5. Database Configuration

#### AppDatabase Updates
- **File**: `app/src/main/java/com/ner/wimap/data/database/AppDatabase.kt`
- **Changes**:
  - Added `TemporaryNetworkData` to entities list
  - Incremented version to 3
  - Added `temporaryNetworkDataDao()` abstract method
  - Added migration `MIGRATION_2_3` to create the new table

#### Dependency Injection
- **DatabaseModule**: Added `TemporaryNetworkDataDao` provider
- **RepositoryModule**: Added `TemporaryNetworkDataRepository` binding

### 6. UI Integration

#### MainActivity Updates
- **File**: `app/src/main/java/com/ner/wimap/MainActivity.kt`
- **Changes**:
  - Updated `onUpdateNetworkData` to use `updateTemporaryNetworkData()`
  - Updated pin/unpin operations to use `pinNetworkWithTemporaryData()`

## Data Flow

1. **User Input**: User adds comment, password, or image to a network card
2. **Immediate Save**: Data is immediately saved to `temporary_network_data` table via `updateTemporaryNetworkData()`
3. **Reactive Updates**: `enhancedNetworks` flow automatically combines scan results with saved temporary data
4. **Persistence**: Data persists across scan refreshes, app restarts, and sessions
5. **Cleanup**: Old temporary data is periodically cleaned up based on `lastUpdated` timestamp

## Key Benefits

1. **Immediate Persistence**: User data is saved instantly, not just when pinning
2. **Seamless Experience**: Data automatically reappears when networks are rediscovered
3. **Unified System**: Same data structure for both pinned and unpinned networks
4. **Performance**: Reactive flows ensure UI updates automatically when data changes
5. **Data Integrity**: BSSID-based linking ensures data stays with the correct network

## Migration Strategy

- Database version incremented from 2 to 3
- Migration script creates `temporary_network_data` table
- Fallback to destructive migration for development (should be removed for production)
- Existing pinned networks continue to work unchanged

## Testing Scenarios

1. **Add comment to unpinned network** → Refresh scan → Comment should persist
2. **Add password to unpinned network** → Refresh scan → Password should persist  
3. **Attach image to unpinned network** → Refresh scan → Image should persist
4. **Pin network with existing temporary data** → Data should transfer to pinned networks table
5. **Unpin network** → Data should remain in temporary data table
6. **App restart** → All temporary data should persist

## Future Enhancements

1. **Data Sync**: Could sync temporary data across devices
2. **Smart Cleanup**: More sophisticated cleanup based on network frequency
3. **Export Integration**: Include temporary data in export functionality
4. **Analytics**: Track which temporary data is most valuable to users