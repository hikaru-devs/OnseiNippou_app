package com.example.onseinippou.application.socket;

import com.example.onseinippou.infra.stt.SpeechToTextClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TranscriptionSocketHandlerUT2Test {

    @LocalServerPort
    private int port;

    @MockBean
    private SpeechToTextClient speechToTextClient;

    private URI serverUri;

    @BeforeEach
    void setUp() {
        this.serverUri = URI.create("ws://localhost:" + port + "/ws/transcribe");
    }

    @Test
    void testTranscriptionFlow() throws Exception {
        // --- 1. テストの準備 ---

        // サーバーからのメッセージを待つためのラッチ（1つのメッセージを期待）
        final CountDownLatch latch = new CountDownLatch(1);
        // 受信したメッセージを保持する変数
        final var receivedMessage = new StringBuilder();

        // 2. モックの設定：SpeechToTextClientが呼び出されたときの動作を定義
        ArgumentCaptor<Consumer<String>> onResultCaptor = ArgumentCaptor.forClass(Consumer.class);

        // speechToTextClient.startStreamingRecognize が呼び出されたら、
        // onResultコールバックを取得し、即座にダミーのテキストで実行するよう設定
        doAnswer(invocation -> {
            Consumer<String> onResult = invocation.getArgument(0);
            onResult.accept("テスト成功");
            // AudioStreamObserverのモックを返す（今回は何もしないダミーでOK）
            return new SpeechToTextClient.AudioStreamObserver(null, null) {
                @Override public void sendAudio(byte[] audioBytes) {}
                @Override public void closeStream() {}
            };
        }).when(speechToTextClient).startStreamingRecognize(onResultCaptor.capture(), any());


        // 3. WebSocketクライアントの準備
        StandardWebSocketClient client = new StandardWebSocketClient();
        // サーバーからのメッセージを処理するハンドラー
        TextWebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                receivedMessage.append(message.getPayload());
                latch.countDown(); // メッセージを受信したのでラッチを解放
            }
        };

        // --- 4. テスト実行 ---

        // サーバーに接続
        WebSocketSession session = client.doHandshake(handler, serverUri.toString()).get(5, TimeUnit.SECONDS);

        // ダミーの音声データ（バイナリメッセージ）を送信
        session.sendMessage(new org.springframework.web.socket.BinaryMessage(ByteBuffer.wrap("dummy-audio".getBytes())));

        // --- 5. 結果の検証 ---

        // サーバーからメッセージが返ってくるのを最大10秒待つ
        boolean messageReceived = latch.await(10, TimeUnit.SECONDS);

        // セッションを閉じる
        session.close();

        // 表明（アサーション）
        assertThat(messageReceived).isTrue(); // メッセージが時間内に受信できたことを確認
        // 受信したメッセージに、モックが返したダミーテキストが含まれていることを確認
        assertThat(receivedMessage.toString()).contains("テスト成功");
    }
}
