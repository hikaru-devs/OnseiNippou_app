package com.example.onseinippou.application.socket;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import com.example.onseinippou.service.AudioService;

/**
 * クライアントからWebSocket接続要求がある場合、以下のライフサイクルを管理する.
 */
@Component
public class TranscriptionSocketHandler extends BinaryWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptionSocketHandler.class);

    /** 音声処理を担当するサービス. */
    private final AudioService audioService;

    /**
     * コンストラクター。
     * @param audioService インジェクションされるAudioServiceのインスタンス
     */
    public TranscriptionSocketHandler(AudioService audioService) {
        this.audioService = audioService;
    }

    /**
     * 接続確立時、処理を実行する。
     * @param session 確立されたWebSocketセッション
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            audioService.startStreamingTranscription(session);
        } catch (UncheckedIOException e) {
            // AudioServiceの初期化失敗は回復不可能なので、エラーを通知してセッションを閉じる
            logger.error("ストリーミングパイプラインの初期化に失敗しました。 Session: {}\n", session.getId(), e);
            sendErrorMessageAndClose(session, "文字起こしセッションの初期化に失敗しました。");
        }
        // ※予期せぬRuntimeExceptionは、バグとして開発中に検知・修正すべきという思想に基づき、ここでは捕捉しない
    }

    /**
     * クライアントからバイナリデータを受信した際に処理を実行する.
     * @param session メッセージを送信したWebSocketセッション
     * @param message 受信したバイナリメッセージ
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        // AudioServiceが自己回復するため、Handler層での例外処理は不要
        byte[] payloadCopy = new byte[message.getPayloadLength()];
        message.getPayload().get(payloadCopy);
        audioService.processAudioChunk(session, payloadCopy);
    }

    /**
     * WebSocketクライアントとの接続が切断された後に処理を実行する。
     * @param session 切断されたWebSocketセッション
     * @param status  接続のクローズステータス
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        // AudioServiceは内部で例外をログ記録するため、Handler層での例外処理は不要
        audioService.stopStreamingTranscription(session);
    }

    /**
     * クライアントにエラーメッセージを送信し、セッションを閉じるヘルパーメソッド。
     * @param session 対象のWebSocketセッション
     * @param errorMessage クライアントに送信するエラーメッセージ
     */
    private void sendErrorMessageAndClose(WebSocketSession session, String errorMessage) {
        try {
            if (session.isOpen()) {
                String jsonError = String.format("{\"error\": \"%s\", \"status\": 500}", errorMessage);
                session.sendMessage(new TextMessage(jsonError));
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (IOException e) {
            logger.error("エラーメッセージの送信とセッションクローズに失敗しました。 Session: {}\n", session.getId(), e);
        }
    }
}