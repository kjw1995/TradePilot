package com.kjw.tradepilot.common.web;

import java.time.Instant;
import java.util.List;

record ApiError(String code, String message, List<String> details, Instant timestamp) {
}
