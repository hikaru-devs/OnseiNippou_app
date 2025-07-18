package com.example.OnseiNippou_app.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.OnseiNippou_app.Entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}

// Optional<UserEntity> findByEmail(String email);
//Optional<UserEntity> を使うメリット・意図・正当性まとめ

/*
* 1. Null安全性の向上
*    - Optionalを返すことで「値が存在しない」場合を明示的に型で表現できる。
*    - 呼び出し側でnullチェックを強制し、NullPointerExceptionのリスクを減らせる。
*
* 2. 意図の明確化
*    - メソッドのシグネチャだけで「値が無い場合がある」ことが明確になる。
*    - APIの利用者や他の開発者にも意図が伝わりやすい。
*
* 3. 関数型スタイルの利便性
*    - Optionalのmap, ifPresent, orElseなどのメソッドでnullチェック不要な処理が書ける。
*    - 処理のチェーンや条件分岐が簡単になる。
*
* 4. バグの早期発見
*    - 値の有無を必ず考慮するため、nullを放置して起こるバグを減らせる。
*
* 5. Spring Data JPAの推奨スタイル
*    - Spring Data JPAでもOptionalな戻り値は公式にサポートされており、findById等もOptionalを返す。
*    - モダンなJava/Springアプリでは標準的な設計。
*/

/*
 * Optionalの使い方（サンプル&解説）
 *
 * UserRepository#findByEmail(email) の戻り値は Optional<User>
 * 検索結果が「ある/なし」に応じて、以下のように書ける。
 */

// Optional<User> optionalUser = userRepository.findByEmail("test@example.com");

// 1. 値が存在する場合のみ処理したい（ifPresent）
// optionalUser.ifPresent(user -> {
    // userが存在する場合だけここが実行される
    // System.out.println("ユーザー名: " + user.getUsername());
// });

// 2. 値が存在しない場合はデフォルト値や例外にしたい（orElse, orElseThrow）
// User userOrDefault = optionalUser.orElse(new UserEntity(/* デフォルト値をセット */));

// 値がなければ例外を投げる（よく使うパターン）
// User userOrException = optionalUser.orElseThrow(() -> new RuntimeException("ユーザーが見つかりません"));

// 3. mapで値変換や取り出しもできる
// String username = optionalUser.map(User::getUsername).orElse("NoUser");

// 4. isPresent()/isEmpty()で値の有無を確認（Java 11以降はisEmptyも可）
// if (optionalUser.isPresent()) {
    // userが存在する場合の処理
// } else {
    // userが存在しない場合の処理
// }

/*
 * --- まとめ ---
 * Optionalを使うことで「値がある場合・ない場合」を明示的・安全に扱える！
 * nullチェックよりも可読性・保守性が高くなるので積極的に活用しよう。
 */

