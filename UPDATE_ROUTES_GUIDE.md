# Guide for Updating Routes to Use TransactionProvider

## Pattern for Route Updates

### 1. Add Import
Add the TransactionType import to the existing imports:
```kotlin
import io.heapy.kotbusta.database.TransactionType
```

### 2. Get TransactionProvider
In the route function, add transactionProvider:
```kotlin
val transactionProvider = applicationFactory.transactionProvider.value
```

### 3. Wrap Service Calls

#### For READ operations (GET routes):
```kotlin
val result = transactionProvider.transaction(TransactionType.READ_ONLY) {
    service.method(params)
}
```

#### For WRITE operations (POST/PUT/DELETE routes):
```kotlin
val result = transactionProvider.transaction(TransactionType.READ_WRITE) {
    service.method(params)
}
```

## Example Conversions

### Before (GET route):
```kotlin
val book = bookService.getBookById(bookId, user.userId)
```

### After (GET route):
```kotlin
val book = transactionProvider.transaction(TransactionType.READ_ONLY) {
    bookService.getBookById(bookId, user.userId)
}
```

### Before (POST route):
```kotlin
val success = bookService.starBook(user.userId, bookId)
```

### After (POST route):
```kotlin
val success = transactionProvider.transaction(TransactionType.READ_WRITE) {
    bookService.starBook(user.userId, bookId)
}
```

## Routes to Update

### Books Routes
- [x] GetBookByIdRoute.kt (READ_ONLY)
- [x] StarBookRoute.kt (READ_WRITE)
- [ ] GetBooksRoute.kt (READ_ONLY)
- [ ] BooksSearchRoute.kt (READ_ONLY)
- [ ] GetSimilarBooksRoute.kt (READ_ONLY)
- [ ] GetBookCoverRoute.kt (READ_ONLY)
- [ ] GetBookCommentsRoute.kt (READ_ONLY)
- [ ] GetStarredBooksRoute.kt (READ_ONLY)
- [ ] UnstarBookRoute.kt (READ_WRITE)
- [ ] AddBookCommentRoute.kt (READ_WRITE)
- [ ] DownloadBookRoute.kt (READ_ONLY + READ_WRITE for recording download)

### User Routes
- [ ] UserInfoRoute.kt (READ_ONLY)

### Auth Routes
- [ ] LoginRoute.kt (READ_WRITE)
- [ ] LogoutRoute.kt (READ_WRITE)
- [ ] GoogleOauthRoutes.kt (READ_WRITE)

### Activity Routes
- [ ] GetActivityRoute.kt (READ_ONLY)

### Comments Routes
- [ ] UpdateCommentRoute.kt (READ_WRITE)
- [ ] DeleteCommentRoute.kt (READ_WRITE)

### Notes Routes
- [ ] AddOrUpdateNoteRoute.kt (READ_WRITE)
- [ ] DeleteNoteRoute.kt (READ_WRITE)

### Admin Routes
- [ ] AdminRoutes.kt (Various)
- [ ] ImportRoute.kt (READ_WRITE)
- [ ] ExtractCoversRoute.kt (READ_WRITE)
- [ ] GetJobRoute.kt (READ_ONLY)
- [ ] GetJobsRoute.kt (READ_ONLY)
- [ ] StatusRoute.kt (READ_ONLY)