package com.example.onseinippou.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
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
	private ArgumentCaptor<TextMessage> sentMessageCaptor;

	private WebSocketSession mockSession;
	private SpeechToTextClient.AudioStreamObserver mockAudioStreamObserver;

	@BeforeEach
	void setUp() throws Exception {
		mockSession = mock(WebSocketSession.class);
		when(mockSession.getId()).thenReturn("test-session-123");
		when(mockSession.isOpen()).thenReturn(true);

		mockAudioStreamObserver = mock(SpeechToTextClient.AudioStreamObserver.class);

		when(mockSpeechToTextClient.startStreamingRecognize(
				onResultCaptor.capture(),
				onIdleTimeoutCaptor.capture(),
				onErrorCaptor.capture()))
						.thenReturn(mockAudioStreamObserver);
	}

	@Test
	@DisplayName("正常系 1-1 & 1-2統合: 結果を蓄積し、セッション終了時に全文を一括送信する")
	void happyPath_accumulatesTranscriptsAndSendsOnStop() throws Exception {
		// 1. 【準備】セッションを開始
		audioService.startStreamingTranscription(mockSession);

		// 2. 【実行】STTから複数回、結果が返ってきたことをシミュレート
		onResultCaptor.getValue().accept("こんにちは。");
		onResultCaptor.getValue().accept("今日の天気は晴れです。");

		// 3. 【検証】この時点では、まだクライアントに何も送信されていないことを確認
		verify(mockSession, never()).sendMessage(any(TextMessage.class));

		// 4. 【実行】セッションの終了処理を呼び出す
		audioService.stopStreamingTranscription(mockSession);

		// 5. 【検証】終了処理の中で、溜め込んだ全文がまとめて1回だけ送信されたことを確認
		String expectedTranscript = "こんにちは。今日の天気は晴れです。";
		String expectedJson = "{\"transcript\": \"" + expectedTranscript + "\"}";
		verify(mockSession, times(1)).sendMessage(new TextMessage(expectedJson));

		// リソース解放処理も正しく呼ばれたことを確認
		verify(mockAudioStreamObserver, times(1)).closeStream();
	}

	@Test
	@DisplayName("正常系 1-3: 複数セッションが互いに影響せず独立して動作する")
	void happyPath_multipleSessionsOperateIndependently() throws Exception {
		// 1. 【準備】2つのセッションと、それに対応するモックを用意する
		// --- セッション1用のモック ---
		WebSocketSession mockSession1 = mock(WebSocketSession.class);
		when(mockSession1.getId()).thenReturn("session-1");
		when(mockSession1.isOpen()).thenReturn(true);
		SpeechToTextClient.AudioStreamObserver mockObserver1 = mock(SpeechToTextClient.AudioStreamObserver.class);

		// --- セッション2用のモック ---
		WebSocketSession mockSession2 = mock(WebSocketSession.class);
		when(mockSession2.getId()).thenReturn("session-2");
		when(mockSession2.isOpen()).thenReturn(true);
		SpeechToTextClient.AudioStreamObserver mockObserver2 = mock(SpeechToTextClient.AudioStreamObserver.class);

		// STTクライアントが呼ばれるたびに、異なるObserverを返すように設定
		when(mockSpeechToTextClient.startStreamingRecognize(onResultCaptor.capture(), onIdleTimeoutCaptor.capture(),
				onErrorCaptor.capture()))
						.thenReturn(mockObserver1)
						.thenReturn(mockObserver2);

		// 2. 【準備】コールバック関数(onResult)を格納するためのリストを手動で作成
		final List<Consumer<String>> capturedOnResultConsumers = new ArrayList<>();

		// 3. 【準備】Answerを使い、startStreamingRecognizeが呼ばれるたびの動作を定義
		when(mockSpeechToTextClient.startStreamingRecognize(any(Consumer.class), any(Runnable.class),
				any(Consumer.class)))
						.thenAnswer((InvocationOnMock invocation) -> {
							// 呼び出された際の1番目の引数(onResult)を取得
							Consumer<String> onResult = invocation.getArgument(0);
							// 手動でリストに追加する
							capturedOnResultConsumers.add(onResult);

							// リストのサイズ（呼び出し回数）に応じて、返すObserverを切り替える
							if (capturedOnResultConsumers.size() == 1) {
								return mockObserver1; // 1回目の呼び出しではObserver1を返す
							} else {
								return mockObserver2; // 2回目の呼び出しではObserver2を返す
							}
						});

		// 4. 【実行】両方のセッションを開始する
		audioService.startStreamingTranscription(mockSession1);
		audioService.startStreamingTranscription(mockSession2);

		// 5. 【実行】手動で作成したリストを使って、各セッションのコールバックを呼び出す
		capturedOnResultConsumers.get(0).accept("セッション1のテキスト。"); // 1番目のコールバック（セッション1用）
		capturedOnResultConsumers.get(1).accept("セッション2のテキスト。"); // 2番目のコールバック（セッション2用）

		// 6. 【検証】(検証部分は変更なし)
		audioService.stopStreamingTranscription(mockSession1);
		String expectedJson1 = "{\"transcript\": \"セッション1のテキスト。\"}";
		verify(mockSession1, timeout(1000)).sendMessage(new TextMessage(expectedJson1));
		verify(mockSession2, never()).sendMessage(any(TextMessage.class));

		audioService.stopStreamingTranscription(mockSession2);
		String expectedJson2 = "{\"transcript\": \"セッション2のテキスト。\"}";
		verify(mockSession2, timeout(1000)).sendMessage(new TextMessage(expectedJson2));

	}

	@Test
	@DisplayName("正常系 1-4: アップロードされた音声ファイルが正しく文字起こしされる")
	void fileBased_transcribesSuccessfully() throws Exception {
		// 1. 【準備】
		// 1-1. src/test/resources からテスト用の音声ファイルを読み込む
		ClassPathResource resource = new ClassPathResource("test_audio.webm");
		InputStream audioStream = resource.getInputStream();

		MockMultipartFile mockFile = new MockMultipartFile(
				"file",
				"audio.webm",
				"audio/webm",
				audioStream);

		// 1-2. privateメソッドのスタブは不要なので削除

		// 1-3. STTクライアントの振る舞いを定義
		String expectedTranscript = "ファイルからの文字起こしテストです。";
		when(mockSpeechToTextClient.recognizeFromWav(anyString())).thenReturn(expectedTranscript);

		// 2. 【実行】テスト対象のメソッドを呼び出す
		//    内部で実際にffmpegが実行される
		String actualTranscript = audioService.transcribe(mockFile);

		// 3. 【検証】
		assertEquals(expectedTranscript, actualTranscript);
		verify(mockSpeechToTextClient, times(1)).recognizeFromWav(anyString());
	}

	@Test
	@DisplayName("異常系 2-1: STT APIのエラー通知で回復処理が開始される")
	void testErrorRecovery_WhenSttApiFails() throws Exception {
		// 【準備】
		// 最初のstartStreamingTranscription呼び出しは成功させる
		audioService.startStreamingTranscription(mockSession);

		// 【実行】
		// STT APIからエラーが来たことをシミュレートし、回復処理を開始させる
		onErrorCaptor.getValue().accept(new RuntimeException("Simulated STT API error"));

		// 【検証】
		// スパイを使わず、外部モックへの影響を検証する
		var inOrder = inOrder(mockSession, mockAudioStreamObserver, mockSpeechToTextClient);

		// 1. クライアントに"reconnecting"メッセージが送信される
		inOrder.verify(mockSession, timeout(2000))
				.sendMessage(new TextMessage("{\"status\": \"reconnecting\"}"));

		// 2. 古いストリーム(Observer)が閉じられる (stop処理の一部)
		inOrder.verify(mockAudioStreamObserver, timeout(1000)).closeStream();

		// 3. 新しいストリームを開始しようとする (restart処理)
		//    ⇒ startStreamingRecognizeが再度呼ばれる
		inOrder.verify(mockSpeechToTextClient, timeout(2000))
				.startStreamingRecognize(any(Consumer.class), any(Runnable.class), any(Consumer.class));

		// トータルで2回呼ばれていることを確認
		verify(mockSpeechToTextClient, times(2))
				.startStreamingRecognize(any(Consumer.class), any(Runnable.class), any(Consumer.class));
	}

	// TODO: シナリオ3（ffmpegへの書き込み失敗）のテストケースを追加

	@Test
	@DisplayName("異常系2-2: ffmpegへの書き込み失敗で回復処理が開始される")
	void recovery_restartsSession_whenFfmpegPipeIsBroken() throws Exception {
		// 1. 【準備】ストリーミングセッションを開始する
		audioService.startStreamingTranscription(mockSession);

		//    リフレクションを使い、AudioService内部のprivateな`sessions`マップから
		//    ffmpegの入力ストリーム(ffmpegInput)を取得する
		//    - ReflectionTestUtilsはSpringが提供するテスト用のユーティリティ
		Map<WebSocketSession, Object> sessions = (Map<WebSocketSession, Object>) ReflectionTestUtils
				.getField(audioService, "sessions");
		Object context = sessions.get(mockSession);
		OutputStream ffmpegInput = (OutputStream) ReflectionTestUtils.getField(context, "ffmpegInput");

		//    ストリームを強制的に閉じて、パイプが壊れた状態をシミュレート
		ffmpegInput.close();

		// 2. 【実行】この状態で音声データを書き込むと、内部でIOExceptionが発生するはず
		audioService.processAudioChunk(mockSession, new byte[] { 4, 5, 6 });

		// 3. 【検証】STT APIエラー時と同様の回復処理が実行されることを確認
		var inOrder = inOrder(mockSession, mockAudioStreamObserver, mockSpeechToTextClient);

		// 3-1. クライアントに"reconnecting"メッセージが送信される
		inOrder.verify(mockSession, timeout(2000))
				.sendMessage(new TextMessage("{\"status\": \"reconnecting\"}"));

		// 3-2. 古いストリームが閉じられる
		inOrder.verify(mockAudioStreamObserver, timeout(1000)).closeStream();

		// 3-3. 新しいストリームを開始しようとする（合計2回呼ばれる）
		verify(mockSpeechToTextClient, timeout(2000).times(2))
				.startStreamingRecognize(any(Consumer.class), any(Runnable.class), any(Consumer.class));
	}

	@Test
	@DisplayName("異常系 2-3: 回復処理に失敗した場合、最終的にエラーを通知してセッションをクローズする")
	void recoveryFails_finallyClosesSession() throws Exception {
		// 1. 【準備】このテスト専用のwhen設定で、Captorを正しく使う
		when(mockSpeechToTextClient.startStreamingRecognize(onResultCaptor.capture(), onIdleTimeoutCaptor.capture(),
				onErrorCaptor.capture()))
						.thenReturn(mockAudioStreamObserver) // 1回目は成功
						.thenThrow(new IOException("Failed to reconnect to STT API")); // 2回目は失敗

		// 2. 【実行】
		audioService.startStreamingTranscription(mockSession);
		// ↓ この行でエラーが発生しなくなる
		onErrorCaptor.getValue().accept(new RuntimeException("Initial STT API error"));

		// 3. 【検証】
		// 3-1. sendMessageが合計2回呼ばれ、その内容をすべてキャプチャする
		verify(mockSession, timeout(2500).times(2)).sendMessage(sentMessageCaptor.capture());

		// 3-2. キャプチャしたメッセージをリストとして取得
		List<TextMessage> sentMessages = sentMessageCaptor.getAllValues();

		// 3-3. 1通目のメッセージが "reconnecting" であることを確認
		assertEquals("{\"status\": \"reconnecting\"}", sentMessages.get(0).getPayload());

		// 3-4. 2通目のメッセージが "回復不可能なエラー" であることを確認
		assertTrue(sentMessages.get(1).getPayload().contains("{\"error\": \"回復不可能なエラーが発生しました。\"}"));

		// 3-5. 最終的にセッションがサーバエラーとしてクローズされたことを確認
		verify(mockSession, timeout(1000)).close(CloseStatus.SERVER_ERROR);
	}

	@Test
	@DisplayName("エッジケース 3-1: 存在しないセッションに対する操作は安全に無視される")
	void edgeCase_operationsOnNonExistentSessionDoNothing() {
		// 1. 【準備】
		// このテストではセッションを開始しない。
		// `mockSession` は存在するが、AudioServiceの内部マップには登録されていない状態。

		// 2. 【実行】と【検証】
		//    存在しないセッションに対して各メソッドを呼び出しても、
		//    例外がスローされないことを確認する。

		// 2-1. 音声データを処理しようとする
		assertDoesNotThrow(() -> {
			audioService.processAudioChunk(mockSession, new byte[] { 1, 2, 3 });
		}, "processAudioChunkは例外をスローすべきではない");

		// 2-2. ストリームを停止しようとする
		assertDoesNotThrow(() -> {
			audioService.stopStreamingTranscription(mockSession);
		}, "stopStreamingTranscriptionは例外をスローすべきではない");

		// 3. 【追加検証】
		//    STTクライアントとのやり取りが一切発生していないことを確認
		verifyNoInteractions(mockSpeechToTextClient);
	}

	@Test
	@DisplayName("エッジケース 3-2: 停止済みのセッションを再度停止してもエラーにならない")
	void edgeCase_stoppingAnAlreadyStoppedSessionIsSafe() {
		// 1. 【準備】セッションを開始する
		audioService.startStreamingTranscription(mockSession);

		// 2. 【実行】同じセッションに対して、stopを2回呼び出す
		audioService.stopStreamingTranscription(mockSession);

		// 2回目も例外がスローされないことを確認
		assertDoesNotThrow(() -> {
			audioService.stopStreamingTranscription(mockSession);
		}, "stopStreamingTranscriptionの2回目以降の呼び出しは例外をスローすべきではない");

		// 3. 【検証】
		//    内部のクリーンアップ処理(closeStream)が、最初の1回しか呼ばれていないことを確認
		verify(mockAudioStreamObserver, times(1)).closeStream();
	}

}
