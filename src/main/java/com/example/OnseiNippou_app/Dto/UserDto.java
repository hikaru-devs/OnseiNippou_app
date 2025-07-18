package com.example.OnseiNippou_app.Dto;

public class UserDto {
    private String email;
    private String sheetId;

    public UserDto(String email, String sheetId) {
        this.email = email;
        this.sheetId = sheetId;
    }
    public String getEmail() { return email; }
    public String getSheetId() { return sheetId; }
}
