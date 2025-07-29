package com.example.onseinippou.application.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.onseinippou.service.UserService;

import lombok.Data;


@RestController
@RequestMapping("/api/register")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /*
     * RegisterUserRequest
     * - ユーザー登録用リクエストDTO
     * - @Validでバリデーション付与
     */
    @Data
    public static class RegisterUserRequest {
        @Email(message = "有効なメールアドレスを入力してください")
        @NotBlank(message = "メールアドレスは必須です")
        private String email;

        @NotBlank(message = "パスワードは必須です")
        private String password;
    }


    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody RegisterUserRequest request) {
        try {
        	userService.simpleRegister(request.getEmail(), request.getPassword());
            // フロントで画面遷移させるのがベストなので、メッセージだけ返す
            return ResponseEntity.ok("ユーザー登録が完了しました。");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("予期しないエラーが発生しました。");
        }
    }
}


/*
 * registerメソッドについて
 *
 * ResponseEntity<?> register(@Valid @RequestBody RegisterUserRequest request)
 * - @Valid：リクエストボディの値を事前にバリデーション（エラー時は自動で400 Bad Request）
 * - @RequestBody：JSON等のリクエストボディをRegisterUserRequestオブジェクトへマッピング
 * RegisterUserRequest：ユーザー登録に必要な値のみを束ねるDTO（email, password）
 * - これは「自分で定義するリクエスト用のDTOクラス」です。
 * - SpringやJava標準で用意されているものではありません。
 * - 例えばユーザー登録APIで「フロントから送られるJSONデータ」を束ねる入れ物です。
 * - コントローラで @RequestBody RegisterUserRequest request と使うことで、
 *   JSONリクエストの値が自動的にこのクラスのフィールドへマッピングされます。
 * 例: {"email":"a@a.com","password":"pass"} が送られた場合 → request.getEmail(), request.getPassword() で取得できる
 * ResponseEntity<?>：汎用的なレスポンス型。
 * 
 */