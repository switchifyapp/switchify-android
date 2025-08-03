# OTP Error Mapping Test Examples

This file demonstrates how various API errors will be mapped to user-friendly messages.

## Test Cases

### Email Not Found Errors
- **API Error**: "Email not found in database"
- **User Sees**: "Email address not found. Please check your email and try again."

- **API Error**: "User not found for email test@example.com"
- **User Sees**: "Email address not found. Please check your email and try again."

### User Already Exists (Sign Up)
- **API Error**: "User already registered with this email"
- **User Sees**: "This email is already registered. Try signing in instead."

### OTP Code Errors
- **API Error**: "Invalid OTP code provided"
- **User Sees**: "Incorrect verification code. Please check your code and try again."

- **API Error**: "OTP token has expired"
- **User Sees**: "Incorrect verification code. Please check your code and try again."

### Rate Limiting
- **API Error**: "Email rate limit exceeded, try again later"
- **User Sees**: "Too many attempts. Please wait a few minutes before trying again."

### Network Issues
- **API Error**: "Network timeout occurred"
- **User Sees**: "Connection problem. Please check your internet and try again."

### Email Format Issues
- **API Error**: "Invalid email format provided"
- **User Sees**: "Please enter a valid email address."

### Server Errors
- **API Error**: "Internal server error 500"
- **User Sees**: "Server temporarily unavailable. Please try again in a few minutes."

### Authentication Errors  
- **API Error**: "Authentication failed for user"
- **User Sees**: "Authentication failed. Please try again."

### Unknown Errors
- **API Error**: "Foreign key constraint violation in table xyz"
- **User Sees**: "Something went wrong. Please try again or contact support if the problem continues."

## Benefits

1. **User-Friendly**: Clear, actionable messages instead of technical jargon
2. **Consistent**: All errors follow the same tone and structure
3. **Debuggable**: Original errors are still logged for developers
4. **Extensible**: Easy to add new error patterns as needed
5. **Tested**: Comprehensive unit tests ensure reliability