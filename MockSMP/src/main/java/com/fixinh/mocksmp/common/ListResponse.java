package com.fixinh.mocksmp.common;

import java.util.List;

/** {@code { "items": [...] } } — the wrapper docs/smp-staff-contract.md recommends. */
public record ListResponse<T>(List<T> items) {
}
