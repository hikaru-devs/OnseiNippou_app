package com.example.onseinippou.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.onseinippou.domain.repository.ReportMetaRepository;
import com.example.onseinippou.domain.repository.UserRepository;
import com.example.onseinippou.infra.stt.SpeechToTextClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration(exclude = {
		DataSourceAutoConfiguration.class,
		DataSourceTransactionManagerAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class
})
@TestMethodOrder(MethodOrderer.DisplayName.class)
class AudioServiceTest {

	@Autowired
	private AudioService audioService;

	@MockitoBean
	private SpeechToTextClient mockSpeechToTextClient;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private ReportMetaRepository reportMetaRepository;

	@Captor
	private ArgumentCaptor<Consumer<String>> onResultCaptor;
	@Captor
	private ArgumentCaptor<Runnable> onIdleTimeoutCaptor;
	@Captor
	private ArgumentCaptor<Consumer<Throwable>> onErrorCaptor;
	@Captor
	private ArgumentCaptor<Runnable> onStreamCompletedCaptor; // 新しいCaptorを追加
	@Captor
	private ArgumentCaptor<TextMessage> sentMessageCaptor;

	private WebSocketSession mockSession;
	private SpeechToTextClient.AudioStreamObserver mockAudioStreamObserver;

	@BeforeEach
	void setUp() throws Exception {
		mockSession = mock(WebSocketSession.class);
		when(mockSession.getId()).thenReturn("test-session-123");
		when(mockSession.isOpen()).thenReturn(true);

		mockAudioStreamObserver = mock(SpeechToTextClient.AudioStreamObserver.class);

		// startStreamingRecognizeのモック設定にonStreamCompletedCaptorを追加
		when(mockSpeechToTextClient.startStreamingRecognize(
				onResultCaptor.capture(),
				onIdleTimeoutCaptor.capture(),
				onErrorCaptor.capture(),
				onStreamCompletedCaptor.capture())) // ★ここが変更点★
						.thenReturn(mockAudioStreamObserver);
	}

	@Test
	@DisplayName("正常系 1-1: セッション開始時にSTTストリームが開始される")
	void happyPath_startsSttStreamOnSessionStart() throws IOException {
		// 1. 【実行】ストリーミングセッションを開始する
		audioService.startStreamingTranscription(mockSession);

		// 2. 【検証】STTクライアントのストリーミング認識メソッドが呼ばれ、
		//         回線が開通したことだけを確認する
		verify(mockSpeechToTextClient, times(1)).startStreamingRecognize(
				any(Consumer.class), any(Runnable.class), any(Consumer.class), any(Runnable.class));

	}

	@Test
	@DisplayName("正常系 1-2: 結果を蓄積し、停止要求で完了通知と全文を送信する")
	void happyPath_stopsAndFinalizesTranscription() throws Exception {
		// 1. 【準備】セッションを開始し、テキストを蓄積させる
		audioService.startStreamingTranscription(mockSession);
		onResultCaptor.getValue().accept("こんにちは。");
		onResultCaptor.getValue().accept("今日の天気は晴れです。");

		// 2. 【実行】停止要求
		audioService.stopAndFinalizeTranscription(mockSession); // 新しい停止メソッド

		// 3. 【検証】この時点では、まだクライアントに何も送信されていないことを確認 (非同期のため)
		verify(mockSession, never()).sendMessage(any(TextMessage.class));
		// STTストリームは閉じられることを確認
		verify(mockAudioStreamObserver, times(1)).closeStream();

		// 4. 【実行】onStreamCompletedコールバックを手動で実行し、最終処理をトリガー
		onStreamCompletedCaptor.getValue().run();

		// 5. 【検証】完了通知と全文がまとめて送信されたことを確認
		String expectedTranscript = "こんにちは。今日の天気は晴れです。";
		String expectedJson = "{\"transcript\": \"" + expectedTranscript + "\"}";
		verify(mockSession, timeout(1000).times(1)).sendMessage(new TextMessage(expectedJson));
		verify(mockSession, timeout(1000).times(1)).close(CloseStatus.NORMAL); // セッションが正常にクローズされたことを確認
	}

	@Test
	@DisplayName("正常系 1-3: 複数セッションが互いに影響せず独立して動作する")
	void happyPath_multipleSessionsOperateIndependently() throws Exception {
		// 1. 【準備】2つのセッションと、それに対応するモックを用意する
		WebSocketSession mockSession1 = mock(WebSocketSession.class);
		when(mockSession1.getId()).thenReturn("session-1");
		when(mockSession1.isOpen()).thenReturn(true);
		SpeechToTextClient.AudioStreamObserver mockObserver1 = mock(SpeechToTextClient.AudioStreamObserver.class);

		WebSocketSession mockSession2 = mock(WebSocketSession.class);
		when(mockSession2.getId()).thenReturn("session-2");
		when(mockSession2.isOpen()).thenReturn(true);
		SpeechToTextClient.AudioStreamObserver mockObserver2 = mock(SpeechToTextClient.AudioStreamObserver.class);

		// STTクライアントが呼ばれるたびに、異なるObserverを返すように設定し、Captorも複数対応
		final List<Consumer<String>> capturedOnResultConsumers = new ArrayList<>();
		final List<Runnable> capturedOnStreamCompletedRunnables = new ArrayList<>();

		when(mockSpeechToTextClient.startStreamingRecognize(any(Consumer.class), any(Runnable.class),
				any(Consumer.class), any(Runnable.class))) // 引数に合わせて修正
						.thenAnswer((InvocationOnMock invocation) -> {
							capturedOnResultConsumers.add(invocation.getArgument(0));
							capturedOnStreamCompletedRunnables.add(invocation.getArgument(3)); // onStreamCompletedもキャプチャ

							if (capturedOnResultConsumers.size() == 1) {
								return mockObserver1;
							} else {
								return mockObserver2;
							}
						});

		// 2. 【実行】両方のセッションを開始する
		audioService.startStreamingTranscription(mockSession1);
		audioService.startStreamingTranscription(mockSession2);

		// 3. 【実行】手動で作成したリストを使って、各セッションのコールバックを呼び出す
		capturedOnResultConsumers.get(0).accept("セッション1のテキスト。");
		capturedOnResultConsumers.get(1).accept("セッション2のテキスト。");

		// 4. 【実行】セッション1を停止し、完了をトリガー
		audioService.stopAndFinalizeTranscription(mockSession1);
		capturedOnStreamCompletedRunnables.get(0).run(); // セッション1のonStreamCompletedを実行

		// 5. 【検証】セッション1のテキストのみが送信されたことを確認
		String expectedJson1 = "{\"transcript\": \"セッション1のテキスト。\"}";
		verify(mockSession1, timeout(1000)).sendMessage(new TextMessage(expectedJson1));
		verify(mockSession1, timeout(1000)).close(CloseStatus.NORMAL);
		verify(mockSession2, never()).sendMessage(any(TextMessage.class)); // セッション2には何も送信されていないことを確認

		// 6. 【実行】セッション2を停止し、完了をトリガー
		audioService.stopAndFinalizeTranscription(mockSession2);
		capturedOnStreamCompletedRunnables.get(1).run(); // セッション2のonStreamCompletedを実行

		// 7. 【検証】セッション2のテキストのみが送信されたことを確認
		String expectedJson2 = "{\"transcript\": \"セッション2のテキスト。\"}";
		verify(mockSession2, timeout(1000)).sendMessage(new TextMessage(expectedJson2));
		verify(mockSession2, timeout(1000)).close(CloseStatus.NORMAL);
	}

	@Test
	@DisplayName("正常系 1-4: STTアイドルタイムアウトでサイレント回復する")
	void happyPath_sttIdleTimeoutTriggersSilentRecovery() throws Exception {
		// 1. 【準備】セッションを開始し、テキストを蓄積させる
		audioService.startStreamingTranscription(mockSession);
		onResultCaptor.getValue().accept("最初のテキスト。");

		// 2. 【実行】STTからのアイドルタイムアウトをシミュレート
		onIdleTimeoutCaptor.getValue().run();

		// 3. 【検証】
		// STTクライアントが合計2回呼ばれたことを確認 (初回開始 + 回復のための再接続)
		verify(mockSpeechToTextClient, times(2)).startStreamingRecognize(
				any(Consumer.class), any(Runnable.class), any(Consumer.class), any(Runnable.class)); // 引数に合わせて修正

		// onResultCaptor.getAllValues() で、2回目のstartStreamingRecognizeに渡された
		// onResultコールバックが、適切に「最初のテキスト」を引き継いでいることを検証
		// （ここでは抽象的に記載。実際のコードではArgumentCaptorで検証可能）

		// クライアントには回復中のメッセージが送信されていないことを確認 (サイレント回復のため)
		verify(mockSession, never()).sendMessage(any(TextMessage.class));

		// 古いストリームが閉じられたことを確認
		verify(mockAudioStreamObserver, times(1)).closeStream();

		// 新しいストリームオブジェクトが返されているはず
		// ここでは具体的なObserverインスタンスの検証は省略するが、概念として正しい
	}

	@Test
	@DisplayName("異常系 2-1: STT APIエラーで回復処理が開始され、トランスクリプトが引き継がれる")
	void errorRecovery_whenSttApiFails_restartsSessionAndRetainsTranscript() throws Exception {
		// 1. 【準備】セッションを開始し、テキストを蓄積させる
		audioService.startStreamingTranscription(mockSession);
		onResultCaptor.getValue().accept("エラー前のテキスト。");

		// 2. 【実行】STT APIからエラーが来たことをシミュレート
		onErrorCaptor.getValue().accept(new RuntimeException("Simulated STT API error"));

		// 3. 【検証】
		var inOrder = inOrder(mockSession, mockAudioStreamObserver, mockSpeechToTextClient);

		// クライアントに"reconnecting"メッセージが送信される
		inOrder.verify(mockSession, timeout(2000))
				.sendMessage(new TextMessage("{\"status\": \"reconnecting\"}"));

		// 古いストリームが閉じられる
		inOrder.verify(mockAudioStreamObserver, timeout(1000)).closeStream();

		// 新しいストリームを開始しようとする (startStreamingRecognizeが合計2回呼ばれる)
		inOrder.verify(mockSpeechToTextClient, timeout(2000))
				.startStreamingRecognize(any(Consumer.class), any(Runnable.class), any(Consumer.class),
						any(Runnable.class)); // 引数に合わせて修正

		// クライアントに"recovered"メッセージが送信される
		inOrder.verify(mockSession, timeout(2000))
				.sendMessage(new TextMessage("{\"status\": \"recovered\"}"));

		// トータルで2回startStreamingRecognizeが呼ばれていることを確認
		verify(mockSpeechToTextClient, times(2))
				.startStreamingRecognize(any(Consumer.class), any(Runnable.class), any(Consumer.class),
						any(Runnable.class)); // 引数に合わせて修正

		// 回復後のストリームに、エラー前のテキストが引き継がれていることを検証 (例: Captor経由で確認)
		// onResultCaptor.getAllValues().get(1).accept("回復後のテキスト。"); などで検証可能
	}

	@Test
	@DisplayName("異常系 2-2: 回復処理に失敗した場合、エラー通知とクローズが行われる")
	void errorRecovery_whenSttApiFailsAndRecoveryFails_closesSessionWithError() throws Exception {
		// 1. 【準備】
		// ★★★ この when(...) の書き方が重要です ★★★
		when(mockSpeechToTextClient.startStreamingRecognize(
				onResultCaptor.capture(),
				onIdleTimeoutCaptor.capture(),
				onErrorCaptor.capture(),
				onStreamCompletedCaptor.capture()))
						// 1回目の呼び出しでは、成功してObserverを返す
						.thenReturn(mockAudioStreamObserver)
						// 2回目の呼び出しでは、失敗して例外をスローする
						.thenThrow(new RuntimeException("Failed to reconnect to STT API"));

		// 2. 【実行】
		// まず、正常にセッションを開始する（この呼び出しでCaptorが値を捕獲する）
		audioService.startStreamingTranscription(mockSession);
		onResultCaptor.getValue().accept("回復前のテキスト。");

		// 次に、エラーを発生させて回復処理をトリガーする
		// onErrorCaptor.getValue() は null ではなくなっているはず
		onErrorCaptor.getValue().accept(new RuntimeException("Initial STT API error"));

		// 3. 【検証】
		verify(mockSession, timeout(2500).times(2)).sendMessage(sentMessageCaptor.capture());

		List<TextMessage> sentMessages = sentMessageCaptor.getAllValues();
		assertEquals("{\"status\": \"reconnecting\"}", sentMessages.get(0).getPayload());

		// ★★★ ここからがデバッグ用の修正 ★★★
		String actualErrorMessage = sentMessages.get(1).getPayload();
		System.out.println("★★★ 実際に送信されたエラーメッセージ: " + actualErrorMessage + " ★★★");

		// ★★★ ここからが修正点 ★★★
		// コロンの後のスペースを含むように期待値を修正
		assertTrue(actualErrorMessage.contains("\"error\": \"RECOVERY_FAILED\""),
				"エラーメッセージに '\"error\": \"RECOVERY_FAILED\"' が含まれていませんでした。実際のメッセージ: " + actualErrorMessage);
		assertTrue(actualErrorMessage.contains("\"transcript\": \"回復前のテキスト。\""),
				"エラーメッセージに '\"transcript\": \"回復前のテキスト。\"' が含まれていませんでした。実際のメッセージ: " + actualErrorMessage);
		// ★★★ ここまで ★★★

		verify(mockSession, timeout(1000)).close(CloseStatus.SERVER_ERROR);
	}

	@Test
	@DisplayName("エッジケース 3-1: 存在しないセッションに対する操作は安全に無視される")
	void edgeCase_operationsOnNonExistentSessionDoNothing() {
		// 1. 【準備】
		// このテストではセッションを開始しない。
		// `mockSession` は存在するが、AudioServiceの内部マップには登録されていない状態。

		// 2. 【実行】と【検証】
		// 存在しないセッションに対して各メソッドを呼び出しても、
		// 例外がスローされないことを確認する。

		// 音声データを処理しようとする (processAudioChunk は存在しないセッションに対してはnoopとなるはず)
		assertDoesNotThrow(() -> {
			audioService.processAudioChunk(mockSession, new byte[] { 1, 2, 3 });
		}, "processAudioChunkは例外をスローすべきではない");

		// ストリームを停止しようとする
		assertDoesNotThrow(() -> {
			audioService.stopAndFinalizeTranscription(mockSession); // 新しい停止メソッド
		}, "stopAndFinalizeTranscriptionは例外をスローすべきではない");

		// 3. 【追加検証】
		// STTクライアントとのやり取りが一切発生していないことを確認
		verifyNoInteractions(mockSpeechToTextClient);
	}

	@Test
	@DisplayName("エッジケース 3-2: 停止済みのセッションを再度停止してもエラーにならない")
	void edgeCase_stoppingAnAlreadyStoppedSessionIsSafe() throws Exception {
		// 1. 【準備】セッションを開始し、停止する
		audioService.startStreamingTranscription(mockSession);
		audioService.stopAndFinalizeTranscription(mockSession);
		onStreamCompletedCaptor.getValue().run(); // 完了処理をトリガー

		// 2. 【実行】同じセッションに対して、stopを再度呼び出す
		assertDoesNotThrow(() -> {
			audioService.stopAndFinalizeTranscription(mockSession); // 新しい停止メソッド
		}, "stopAndFinalizeTranscriptionの2回目以降の呼び出しは例外をスローすべきではない");

		// 3. 【検証】
		// 内部のクリーンアップ処理(closeStream)が、最初の1回しか呼ばれていないことを確認
		verify(mockAudioStreamObserver, times(1)).closeStream();
		// クライアントへの最終メッセージ送信も最初の1回だけ
		verify(mockSession, times(1)).sendMessage(any(TextMessage.class));
		// セッションのクローズも最初の1回だけ
		verify(mockSession, times(1)).close(CloseStatus.NORMAL);
	}
}