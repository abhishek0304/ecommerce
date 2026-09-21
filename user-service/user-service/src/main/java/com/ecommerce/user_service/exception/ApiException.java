package com.ecommerce.user_service.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

public class ApiException extends RuntimeException {
	public ApiException(String m) {
		super(m);
	}
}
