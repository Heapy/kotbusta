# Google OAuth Setup for Kotbusta

## Prerequisites

1. A Google Cloud Console account
2. A project created in Google Cloud Console

## Setup Steps

### 1. Enable Google+ API

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project
3. Navigate to "APIs & Services" > "Library"
4. Search for "Google+ API" and enable it

### 2. Create OAuth 2.0 Credentials

1. Go to "APIs & Services" > "Credentials"
2. Click "Create Credentials" > "OAuth client ID"
3. If prompted, configure the OAuth consent screen first:
   - User Type: External
   - App name: Kotbusta
   - User support email: Your email
   - Developer contact: Your email
   - Add scopes: `email`, `profile`
4. For Application type, select "Web application"
5. Name: "Kotbusta Web Client"
6. Add Authorized redirect URIs:
   - `http://localhost:8080/callback`
   - `http://127.0.0.1:8080/callback`
7. Click "Create"

### 3. Configure Environment Variables

Copy the Client ID and Client Secret from the OAuth 2.0 credentials page.

Create a `.env` file in the project root:

```bash
GOOGLE_CLIENT_ID=your-client-id-here
GOOGLE_CLIENT_SECRET=your-client-secret-here
```

Or export them in your shell:

```bash
export GOOGLE_CLIENT_ID="your-client-id-here"
export GOOGLE_CLIENT_SECRET="your-client-secret-here"
```

### 4. Running the Application

```bash
# Make sure environment variables are set
echo $GOOGLE_CLIENT_ID  # Should show your client ID

# Run the application
./gradlew run
```

### 5. Testing OAuth Login

1. Open http://localhost:8080 in your browser
2. Click "Login with Google"
3. You should be redirected to Google's login page
4. After login, you'll be redirected back to http://localhost:8080/callback
5. The app should create a session and redirect to the home page

## Troubleshooting

### "OAuth not configured" error
- Make sure both `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are set
- Check the application logs for "Configuring Google OAuth with client ID..."

### Callback doesn't work
1. Check browser developer tools Network tab for the callback request
2. Verify the redirect URI in Google Console matches exactly: `http://localhost:8080/callback`
3. Make sure you're accessing the app via `http://localhost:8080` (not 127.0.0.1 or other addresses)

### "Authentication failed" error
1. Check application logs for detailed error messages
2. Verify Google+ API is enabled in Google Cloud Console
3. Check that OAuth consent screen is properly configured

### Common Issues

1. **Redirect URI mismatch**: The URI in Google Console must exactly match the callback URL
2. **Using wrong domain**: Use `localhost` not `127.0.0.1` if that's what's configured
3. **HTTPS vs HTTP**: Make sure you're using HTTP for local development
4. **Expired credentials**: If you just created the OAuth app, wait a few minutes for it to propagate

## Production Deployment

For production, you'll need to:

1. Update the redirect URI in Google Console to your production domain
2. Change the `urlProvider` in Authentication.kt to use your production URL
3. Enable HTTPS (required for OAuth in production)
4. Set `cookie.secure = true` in the session configuration