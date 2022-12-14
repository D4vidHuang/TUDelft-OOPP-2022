package server.api;

import commons.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import server.service.SessionManager;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class QuestionControllerTest {
    private QuestionController sut;
    private TestPlayerRepository playerRepo;
    private SessionController sessionCtrl;
    private LeaderboardController leaderboardController;
    private static ActivityController activityCtrl;
    private static TestActivityRepository activityRepo;

    @BeforeAll
    public static void setupAll() {
        activityRepo = new TestActivityRepository();
        activityRepo.save(new Activity("test", 42L, "test", "test"));
        activityRepo.save(new Activity("test2", 43L, "test2", "test2"));
        activityRepo.save(new Activity("test3", 44L, "test3", "test3"));
        activityRepo.save(new Activity("test4", 45L, "test4", "test4"));
        activityCtrl = new ActivityController(new Random(), activityRepo);
    }

    @BeforeEach
    public void setupEach() {
        playerRepo = new TestPlayerRepository();
        leaderboardController = new LeaderboardController(playerRepo);
        sessionCtrl = new SessionController(new Random(), playerRepo, "test", new SessionManager(),
                activityCtrl, new LeaderboardController(playerRepo));

        ResponseEntity<GameSession> cur = sessionCtrl.addSession(
                new GameSession(GameSession.SessionType.MULTIPLAYER, List.of(new Player("test", 0))));
        sut = new QuestionController(sessionCtrl, leaderboardController);
    }

    @Test
    public void testGetQuestionNoSession() {
        ResponseEntity<Question> q = sut.getOneQuestion(42L);
        assertEquals(HttpStatus.BAD_REQUEST, q.getStatusCode());
    }

    @Test
    public void testGetQuestion() {
        GameSession s = sessionCtrl.getAllSessions().get(0);
        ResponseEntity<Question> resp = sut.getOneQuestion(s.id);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Question q = resp.getBody();
        Question serverQuestion = sessionCtrl.getAllSessions().get(0).currentQuestion;

        assertEquals(serverQuestion, q);
    }

    @Test
    public void submitAnswerNoSessionTest() {
        ResponseEntity<Evaluation> resp = sut.submitAnswer(42L,
                42L, new Answer(List.of(0L), Question.QuestionType.MULTIPLE_CHOICE));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    public void submitAnswerNoPlayerTest() {
        GameSession s = sessionCtrl.getAllSessions().get(0);

        ResponseEntity<Evaluation> resp = sut.submitAnswer(s.id,
                42L, new Answer(List.of(0L), Question.QuestionType.MULTIPLE_CHOICE));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    public void submitAnswerSpecialModesTest() {
        GameSession s = sessionCtrl.addSession(new GameSession(GameSession.SessionType.SURVIVAL,
                List.of(new Player("test", 0)))).getBody();

        assertNotNull(s);
        sut.submitAnswer(s.id, s.getPlayers().get(0).id, new Answer(s.expectedAnswers, s.currentQuestion.type));
        Player p = sessionCtrl.getPlayers(s.id).getBody().get(0);
        assertEquals(1, p.currentPoints);
    }

    @Test
    public void submitAnswerMCTest() {
        // Setup MC question
        GameSession s = sessionCtrl.getAllSessions().get(0);
        s.setCurrentQuestion(new Question("test", "test.png", Question.QuestionType.MULTIPLE_CHOICE));
        s.expectedAnswers = List.of(1L);
        sessionCtrl.updateSession(s);

        // Submit answer
        sut.submitAnswer(s.id, s.getPlayers().get(0).id, new Answer(s.expectedAnswers, s.currentQuestion.type));
        Player p = sessionCtrl.getPlayers(s.id).getBody().get(0);
        assertNotEquals(0, p.currentPoints);
    }

    @Test
    public void submitAnswerEstimateTest() {
        // Setup Estimation question
        GameSession s = sessionCtrl.getAllSessions().get(0);
        s.setCurrentQuestion(new Question("test", "test.png", Question.QuestionType.RANGE_GUESS));
        s.expectedAnswers = List.of(1L);
        sessionCtrl.updateSession(s);

        // Submit answer
        sut.submitAnswer(s.id, s.getPlayers().get(0).id, new Answer(s.expectedAnswers, s.currentQuestion.type));
        Player p = sessionCtrl.getPlayers(s.id).getBody().get(0);
        assertNotEquals(0, p.currentPoints);
    }

    @Test
    public void submitAnswerEstimateDiffTest() {
        // Setup Estimation question
        GameSession s = sessionCtrl.getAllSessions().get(0);
        s.setCurrentQuestion(new Question("test", "test.png", Question.QuestionType.RANGE_GUESS));
        s.expectedAnswers = List.of(1L);
        sessionCtrl.updateSession(s);

        // Submit answer
        sut.submitAnswer(s.id, s.getPlayers().get(0).id, new Answer(List.of(10L), s.currentQuestion.type));
        Player p = sessionCtrl.getPlayers(s.id).getBody().get(0);
        assertEquals(0, p.currentPoints);
    }

    @Test
    public void submitAnswerTest() {
        GameSession s = sessionCtrl.getAllSessions().get(0);
        List<Long> expectedAnswers = List.copyOf(s.expectedAnswers);
        Question q = s.currentQuestion;

        ResponseEntity<Evaluation> resp = sut.submitAnswer(s.id,
                s.getPlayers().get(0).id, new Answer(List.of(0L), Question.QuestionType.MULTIPLE_CHOICE));

        Evaluation eval = resp.getBody();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(expectedAnswers, eval.correctAnswers);
        assertEquals(q.type, eval.type);
    }

    @Test
    public void submitAnswerInvalidAnswerTest() {
        // Setup UNKNOWN question
        GameSession s = sessionCtrl.getAllSessions().get(0);
        s.setCurrentQuestion(new Question("test", "test.png", Question.QuestionType.UNKNOWN));
        s.expectedAnswers = List.of(1L);
        sessionCtrl.updateSession(s);

        // Submit answer
        assertThrows(UnsupportedOperationException.class, () -> {
            sut.submitAnswer(s.id,
                    s.getPlayers().get(0).id, new Answer(List.of(0L), Question.QuestionType.UNKNOWN));
        });
    }

    @Test
    public void testGetAnswers() {
        GameSession s = sessionCtrl.getAllSessions().get(0);
        ResponseEntity<List<Long>> resp = sut.getCorrectAnswers(s.id);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<Long> list = resp.getBody();
        List<Long> answers = sessionCtrl.getAllSessions().get(0).expectedAnswers;
        assertEquals(answers, list);
    }

    @Test
    public void testGetAnswersInvalid() {
        ResponseEntity<List<Long>> resp = sut.getCorrectAnswers(42L);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }
}
