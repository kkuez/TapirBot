package tapir;

import net.dv8tion.jda.api.entities.User;
import org.sqlite.javax.SQLiteConnectionPoolDataSource;

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

    public void handleUser(UserWrapper user) {
        final long userId = getUserId(user.getUser());
        if (!knownUsers.contains(userId)) {
            try (Statement statement = getConnection().createStatement();) {
                statement.executeUpdate(
                        "insert into User(id, name) values(" + userId + ",'" + user.getUser().getName() + "')");
            } catch (SQLException e) {
                e.printStackTrace();
            }

            knownUsers.add(userId);
        }
    }

    /***
     * Gets Questions which the user has not yet answered
     */
    public List<QuizQuestion> getFilteredQuestionsForUser(User user) {
        final long userId = getUserId(user);
        List<QuizQuestion> questions = new ArrayList<>();
        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("Select * from QuizQuestions qq inner join User u on " +
                     "u.id=qq.user where not exists (select * from User_QuizQuestions uqq where qq.id=uqq.question " +
                     "and uqq.user=" + userId + ") and not qq.user=" + userId)) {
            while (rs.next()) {
                List<QuizAnswer> answers = new ArrayList<>(4);
                answers.add(new QuizAnswer(rs.getString(Quiz.RIGHT_ANSWER), Quiz.RIGHT_ANSWER));
                answers.add(new QuizAnswer(rs.getString(Quiz.WRONG_ANSWER_1), Quiz.WRONG_ANSWER_1));
                answers.add(new QuizAnswer(rs.getString(Quiz.WRONG_ANSWER_2), Quiz.WRONG_ANSWER_2));
                answers.add(new QuizAnswer(rs.getString(Quiz.WRONG_ANSWER_3), Quiz.WRONG_ANSWER_3));

                QuizQuestion question = new QuizQuestion(
                        rs.getInt("id"),
                        rs.getString("text"),
                        answers,
                        rs.getString("name")
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
                    "insert into User_QuizQuestions(answer, user, question) values('" + answer + "'," + userId
                            + "," + questionId + ")");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Quiz.RankingTableEntry> getUserScoresPointRated() {
        List<Quiz.RankingTableEntry> rankingTable = new ArrayList<>();

        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("select user as userId, " +
                     "(select name from User where id=uuq.user) as userName, " +
                     "(select sum(IIF(answer ='Right_Answer', 3, IIF(answer = 'Keine Ahnung!', 0, -2))) " +
                     "from User_QuizQuestions where user=uuq.user) as points, " +
                     "count(question) as answered " +
                     "from User_QuizQuestions uuq group by user order by points asc")) {
            while (rs.next()) {
                final Quiz.RankingTableEntry rankingTableEntry =
                        new Quiz.RankingTableEntry(
                                rs.getLong("userId"),
                                rs.getString("userName"),
                                rs.getInt("points"),
                                rs.getInt("answered"),
                getQuestionsCreatedByUser(rs.getLong("userId")).size());
                rankingTable.add(rankingTableEntry);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        rankingTable.sort(Comparator.comparing(Quiz.RankingTableEntry::getPoints));
        Collections.reverse(rankingTable);
        return rankingTable;
    }

    public void enterQuestions(User user, QuizQuestion question, List<QuizAnswer> answers) {
        try (Statement statement = getConnection().createStatement();) {
            statement.executeUpdate(
                    "insert into QuizQuestions(text, Right_Answer, Wrong_Answer_1," +
                            "Wrong_Answer_2, Wrong_Answer_3, user) values('" + mangleChars(question.getText()) + "','"
                            + mangleChars(answers.get(0).getText()) + "','" +
                            mangleChars(answers.get(1).getText()) + "','" +
                            mangleChars(answers.get(2).getText()) + "','" +
                            mangleChars(answers.get(3).getText()) + "'," +
                            user.getIdLong() + ")");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String mangleChars(String input) {
        return input.replace("'", "''");
    }

    /**
     * Gets questions which where created by the user
     * */
    public List<QuizQuestion> getQuestionsCreatedByUser(long userId) {
        List<QuizQuestion> questions = new ArrayList<>();
        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("Select * from QuizQuestions where user=" + userId)) {
            while (rs.next()) {
                List<QuizAnswer> answers = new ArrayList<>(4);
                answers.add(new QuizAnswer(rs.getString(Quiz.RIGHT_ANSWER), Quiz.RIGHT_ANSWER));
                answers.add(new QuizAnswer(rs.getString(Quiz.WRONG_ANSWER_1), Quiz.WRONG_ANSWER_1));
                answers.add(new QuizAnswer(rs.getString(Quiz.WRONG_ANSWER_2), Quiz.WRONG_ANSWER_2));
                answers.add(new QuizAnswer(rs.getString(Quiz.WRONG_ANSWER_3), Quiz.WRONG_ANSWER_3));

                QuizQuestion question = new QuizQuestion(
                        rs.getInt("id"),
                        rs.getString("text"),
                        answers,
                        rs.getString("user")
                );
                questions.add(question);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return questions;
    }

}
