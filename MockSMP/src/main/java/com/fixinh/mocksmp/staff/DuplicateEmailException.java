package com.fixinh.mocksmp.staff;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException(String email) {
		super("A staff account already exists with email " + email);
	}
}
