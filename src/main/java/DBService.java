import net.dv8tion.jda.api.entities.User;
import org.sqlite.SQLiteConfig;
import org.sqlite.javax.SQLiteConnectionPoolDataSource;
import org.sqlite.javax.SQLitePooledConnection;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import java.io.File;
import java.sql.*;
import java.util.*;

public class DBService {

    private final String dbPath;
    private Set<Long> knownUsers;
    private SQLiteConnectionPoolDataSource poolDataSource;

    public DBService(Properties properties) {
        dbPath = new File(properties.get("dbPath").toString()).getAbsolutePath();
        readUsers();
        try {
            Class.forName("org.sqlite.JDBC");
            DriverManager.registerDriver(new org.sqlite.JDBC());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        System.out.println();
    }

    private void readUsers() {
        knownUsers = new HashSet<>();

        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("select id from User")) {
            while (rs.next()) {
                knownUsers.add(rs.getLong("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void handleUser(User user) {
        final long userId = getUserId(user);
        if (!knownUsers.contains(userId)) {
            try (Statement statement = getConnection().createStatement();) {
                statement.executeUpdate(
                        "insert into User(id, name) values(" + userId + ",'" + user.getName() + "')");
            } catch (SQLException e) {
                e.printStackTrace();
            }

            knownUsers.add(userId);
        }
    }

    public List<QuizQuestion> getQuestionsForUser(User user) {
        final long userId = getUserId(user);
        List<QuizQuestion> questions = new ArrayList<>();
        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("select * from QuizQuestions qq where not qq.id=" +
                     "(Select question from User_QuizQuestions where user =" + userId + ")")) {
            while (rs.next()) {
                List<QuizAnswer> answers = new ArrayList<>(4);
                answers.add(new QuizAnswer(rs.getString(Quiz.RIGHT_ANSWER), Quiz.RIGHT_ANSWER));
                answers.add(new QuizAnswer(rs.getString(Quiz.WRONG_ANSWER_1), Quiz.WRONG_ANSWER_1));
                answers.add(new QuizAnswer(rs.getString(Quiz.WRONG_ANSWER_2), Quiz.WRONG_ANSWER_2));
                answers.add(new QuizAnswer(rs.getString(Quiz.WRONG_ANSWER_3), Quiz.WRONG_ANSWER_3));

                QuizQuestion question = new QuizQuestion(
                        rs.getInt("id"),
                        rs.getString("text"),
                        answers
                );
                questions.add(question);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return questions;
    }

    private long getUserId(User user) {
        return user.getIdLong();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public void sendAnswer(long userId, int questionId, String answer) {
        try (Statement statement = getConnection().createStatement();) {
            statement.executeUpdate(
                    "insert into User_QuizQuestions(answer, user, question) values('" + answer + "'," + userId + "," + questionId + ")");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Quiz.RankingTableEntry> getUserScoresPointRated() {
        List<Quiz.RankingTableEntry> rankingTable = new ArrayList<>();

        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("select user as userId, (select name from User where " +
                     "id=uuq.user) as userName, (select sum(IIF(answer ='Right_Answer', 3, IIF(answer = " +
                     "'Keine Ahnung!', 0, -2))) from User_QuizQuestions where user=uuq.user) as points from " +
                     "User_QuizQuestions uuq group by points order by points DESC")) {
            while (rs.next()) {
                final Quiz.RankingTableEntry rankingTableEntry =
                        new Quiz.RankingTableEntry(
                                rs.getLong("userId"),
                                rs.getString("userName"),
                                rs.getInt("points"));
                rankingTable.add(rankingTableEntry);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rankingTable;
    }
}
