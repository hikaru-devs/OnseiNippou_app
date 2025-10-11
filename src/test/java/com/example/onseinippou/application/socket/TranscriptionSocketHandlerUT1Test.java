package com.example.onseinippou.application.socket;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.onseinippou.service.AudioService;

@ExtendWith(MockitoExtension.class)
@DisplayName("TranscriptionSocketHandlerのユニットテスト")
class TranscriptionSocketHandlerUT1Test {

	@Mock
	private AudioService audioService;

	@Mock
	private WebSocketSession session;

	@InjectMocks
	private TranscriptionSocketHandler transcriptionSocketHandler;

	@Nested
	@DisplayName("正常系のテスト")
	class HappyPath {

		@Test
		@DisplayName("01 接続確立時にAudioServiceのstartが呼ばれる")
		void afterConnectionEstablished() throws Exception {
			// 実行
			transcriptionSocketHandler.afterConnectionEstablished(session);

			// 検証
			verify(audioService, times(1)).startStreamingTranscription(session);
		}

		@Test
		@DisplayName("02 バイナリメッセージ受信時にAudioServiceのprocessが呼ばれる")
		void handleBinaryMessage() throws Exception {
			// 準備
			byte[] payload = "audio data".getBytes();
			BinaryMessage message = new BinaryMessage(payload);

			// 実行
			transcriptionSocketHandler.handleBinaryMessage(session, message);

			// 検証
			verify(audioService, times(1)).processAudioChunk(eq(session), any(byte[].class));
		}

		@Test
		@DisplayName("03 接続切断時にAudioServiceのstopが呼ばれる")
		void afterConnectionClosed() throws Exception {
			// 実行
			transcriptionSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);

			// 検証
			verify(audioService, times(1)).stopStreamingTranscription(session);
		}
	}

	@Nested
	@DisplayName("異常系のテスト")
	class ErrorPath {

		@Test
		@DisplayName("01 接続確立時にAudioServiceで例外が発生した場合、エラーメッセージを送信してセッションを閉じる")
		void afterConnectionEstablished_throwsException() throws Exception {
			// 準備
			doThrow(new UncheckedIOException("Test Exception", new IOException("Dummy cause"))).when(audioService)
					.startStreamingTranscription(session);
			when(session.isOpen()).thenReturn(true);
			ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);

			// 実行
			transcriptionSocketHandler.afterConnectionEstablished(session);

			// 検証
			verify(session, times(1)).sendMessage(messageCaptor.capture());
			assertThat(messageCaptor.getValue().getPayload()).contains("文字起こしセッションの初期化に失敗しました。");
			verify(session, times(1)).close(CloseStatus.SERVER_ERROR);
		}

		/**
		 * sendErrorMessageAndCloseの異常系テスト
		 * 状況：afterConnectionEstablished内で例外が発生し、エラー通知とセッションクローズの処理に入った、という前提でテストする。
		 */
		@Test
		@DisplayName("02 エラーメッセージ送信時にIOExceptionが発生しても、例外が外部に伝播しない")
		void sendErrorMessage_throwsException() {
			// --- 準備 (Arrange) ---
			// 1. afterConnectionEstablished の catch ブロックに入るように設定
			doThrow(new UncheckedIOException("Init failed", new IOException()))
					.when(audioService).startStreamingTranscription(session);

			// 2. session.isOpen() は true を返すように設定
			when(session.isOpen()).thenReturn(true);

			// 3. ★本テストの核心：session.sendMessage が呼ばれたら、IOExceptionをスローさせる
			try {
				doThrow(new IOException("Simulated send error"))
						.when(session).sendMessage(any(TextMessage.class));
			} catch (IOException e) {
				// doThrowの宣言のためのtry-catchなので、ここでは何もしない
			}

			// --- 実行 & 検証 (Act & Assert) ---
			// afterConnectionEstablished を実行しても、sendErrorMessageAndClose内で例外が握りつぶされるため、
			// テストメソッド自体は正常に完了することを確認する。
			assertThatCode(() -> {
				transcriptionSocketHandler.afterConnectionEstablished(session);
			}).doesNotThrowAnyException();

			// sendMessageが呼ばれたことは確認する。
			try {
				verify(session, times(1)).sendMessage(any(TextMessage.class));
			} catch (IOException e) {
				// ここでもdoThrowの宣言のためのtry-catch
			}
		}

		@Test
		@DisplayName("03 セッションクローズ時にIOExceptionが発生しても、例外が外部に伝播しない")
		void closeSession_throwsException() {
			// --- 準備 (Arrange) ---
			// 1. afterConnectionEstablished の catch ブロックに入るように設定
			doThrow(new UncheckedIOException("Init failed", new IOException()))
					.when(audioService).startStreamingTranscription(session);

			// 2. session.isOpen() は true を返すように設定
			when(session.isOpen()).thenReturn(true);

			// 3. ★本テストの核心：session.close が呼ばれたら、IOExceptionをスローさせる
			try {
				doThrow(new IOException("Simulated close error"))
						.when(session).close(any(CloseStatus.class));
			} catch (IOException e) {
				// doThrowの宣言のためのtry-catch
			}

			// --- 実行 & 検証 (Act & Assert) ---
			// 例外が握りつぶされ、テストメソッドが正常に完了することを確認する。
			assertThatCode(() -> {
				transcriptionSocketHandler.afterConnectionEstablished(session);
			}).doesNotThrowAnyException();

			// sendMessageは正常に呼ばれていることを確認する。
			try {
				verify(session, times(1)).sendMessage(any(TextMessage.class));
			} catch (IOException e) {
				// doThrowの宣言のためのtry-catch
			}
		}

		@Test
		@DisplayName("04 セッションが既に閉じていた場合、メッセージ送信やクローズ処理を試みない")
		void sendErrorMessage_whenSessionIsAlreadyClosed() {
			// --- 準備 (Arrange) ---
			// 1. afterConnectionEstablished の catch ブロックに入るように設定
			doThrow(new UncheckedIOException("Init failed", new IOException()))
					.when(audioService).startStreamingTranscription(session);

			// 2. ★本テストの核心：session.isOpen() は false を返すように設定
			when(session.isOpen()).thenReturn(false);

			// --- 実行 (Act) ---
			transcriptionSocketHandler.afterConnectionEstablished(session);

			// --- 検証 (Assert) ---
			// sendMessage や close が一度も呼ばれないことを確認する。
			try {
				verify(session, never()).sendMessage(any(TextMessage.class));
				verify(session, never()).close(any(CloseStatus.class));
			} catch (IOException e) {
				// doThrowの宣言のためのtry-catch
			}
		}
	}
}
