package com.example.OnseiNippou_app.Service;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class RegisterSheetService {
    private final ConcurrentHashMap<String, String> registry = new ConcurrentHashMap<>();

    public boolean hasSheetId(String email) {
        return registry.containsKey(email);
    }

    public void register(String email, String sheetId) {
        registry.put(email, sheetId);
    }

    public String getSheetId(String email) {
        return registry.get(email);
    }
}
