package com.nathan.tibiastats.util;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public class JsonConverter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static String toJson(Object o){ try { return
            MAPPER.writeValueAsString(o);} catch (Exception e){ throw new
            RuntimeException(e);} }
}