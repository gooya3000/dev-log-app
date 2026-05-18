package com.example.devlogapp.vault;

/**
 * users[] 에서 해당 userId 를 찾을 수 없을 때 발생.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
    }
}
