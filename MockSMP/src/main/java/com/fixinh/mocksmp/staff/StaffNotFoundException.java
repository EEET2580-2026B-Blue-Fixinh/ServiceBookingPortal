package com.fixinh.mocksmp.staff;

public class StaffNotFoundException extends RuntimeException {

	public StaffNotFoundException(String staffId) {
		super("No staff member with id " + staffId);
	}
}
