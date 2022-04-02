package tapir;

import entities.QuizQuestions;
import net.dv8tion.jda.api.entities.User;
import org.sqlite.javax.SQLiteConnectionPoolDataSource;
import tapir.exception.TapirException;
import tapir.pokemon.Pokemon;
import tapir.quiz.QuizModule;
import tapir.quiz.QuizAnswer;
import tapir.quiz.QuizQuestion;

import java.io.File;
import java.sql.*;
import java.util.*;
import javax.persistence.*;

public class DBService {

    private final String dbPath;
    EntityManagerFactory emf = Persistence.createEntityManagerFactory("emf-sqlite");
    private Set<Long> knownUsers;
    private SQLiteConnectionPoolDataSource poolDataSource;

    public DBService(Properties properties) {
        dbPath = new File(properties.get("dbPath").toString().replace("\"", "")).getAbsolutePath();
        readUsers();
        try {
            Class.forName("org.sqlite.JDBC");
            DriverManager.registerDriver(new org.sqlite.JDBC());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    private void readUsers() {
        knownUsers = new HashSet<>();

        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("select id from User")) {
            while (rs.next()) {
                knownUsers.add(rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new TapirException("Could not read Users!", e);
        }
    }

    public void handleUser(UserWrapper user) {
        final long userId = getUserId(user.getUser());
        if (!knownUsers.contains(userId)) {
            try (Statement statement = getConnection().createStatement();) {
                statement.executeUpdate(
                        "insert into User(id, name) values(" + userId + ",'" + user.getUser().getName() + "')");
            } catch (SQLException e) {
                throw new TapirException("Could not handle Users!", e);
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
                answers.add(new QuizAnswer(rs.getString(QuizModule.RIGHT_ANSWER), QuizModule.RIGHT_ANSWER));
                answers.add(new QuizAnswer(rs.getString(QuizModule.WRONG_ANSWER_1), QuizModule.WRONG_ANSWER_1));
                answers.add(new QuizAnswer(rs.getString(QuizModule.WRONG_ANSWER_2), QuizModule.WRONG_ANSWER_2));
                answers.add(new QuizAnswer(rs.getString(QuizModule.WRONG_ANSWER_3), QuizModule.WRONG_ANSWER_3));

                QuizQuestion question = new QuizQuestion(
                        rs.getInt("id"),
                        rs.getString("text"),
                        answers,
                        rs.getString("name")
                );
                questions.add(question);
            }
        } catch (SQLException e) {
            throw new TapirException("Could not get questions for user " + user.getIdLong(), e);
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
            throw new TapirException("Could send answer to user " + userId, e);
        }
    }

    public List<QuizModule.RankingTableEntry> getUserScoresPointRated() {
        List<QuizModule.RankingTableEntry> rankingTable = new ArrayList<>();

        final String sql = "select user as userId, " +
                "(select name from User where id=uuq.user) as userName, " +
                "(select sum(IIF(answer ='Right_Answer', 3, IIF(answer = 'Keine Ahnung!', 0, -2))) " +
                "from User_QuizQuestions where user=uuq.user) as points, " +
                "count(question) as answered " +
                "from User_QuizQuestions uuq" +
                " group by user order by points asc";

        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                final QuizModule.RankingTableEntry rankingTableEntry =
                        new QuizModule.RankingTableEntry(
                                rs.getLong("userId"),
                                rs.getString("userName"),
                                rs.getInt("points"),
                                rs.getInt("answered"),
                                getQuestionsCreatedByUser(rs.getLong("userId")).size());
                rankingTable.add(rankingTableEntry);
            }
        } catch (SQLException e) {
            throw new TapirException("Could not get UserScores!", e);
        }
        rankingTable.sort(Comparator.comparing(entry -> entry.getPoints() + entry.getCreated()));
        Collections.reverse(rankingTable);
        return rankingTable;
    }

    public void enterQuestion(User user, QuizQuestion question, List<QuizAnswer> answers) {
        final EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        final QuizQuestions quizQuestionEntity = new QuizQuestions();
        quizQuestionEntity.setText(question.getText());
        quizQuestionEntity.setRight_Answer(answers.get(0).getText());
        quizQuestionEntity.setWrong_Answer1(answers.get(1).getText());
        quizQuestionEntity.setWrong_Answer2(answers.get(2).getText());
        quizQuestionEntity.setWrong_Answer3(answers.get(3).getText());
        quizQuestionEntity.setUser(user.getIdLong());
        em.persist(quizQuestionEntity);
        em.getTransaction().commit();

        /*try (PreparedStatement statement =
                     getConnection().prepareStatement("insert into QuizQuestions(text, Right_Answer, Wrong_Answer_1, " +
                             "Wrong_Answer_2, Wrong_Answer_3, user) values(?,?,?,?,?,?)")) {
            statement.setString(1, mangleChars(question.getText()));
            statement.setString(2, mangleChars(answers.get(0).getText()));
            statement.setString(3, mangleChars(answers.get(1).getText()));
            statement.setString(4, mangleChars(answers.get(2).getText()));
            statement.setString(5, mangleChars(answers.get(3).getText()));
            statement.setLong(6, user.getIdLong());
            statement.addBatch();
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new TapirException("Could not enter question for user " + user.getIdLong(), e);
        }*/
    }

    private String mangleChars(String input) {
        return input.replace("'", "''");
    }

    /**
     * Gets questions which where created by the user
     */
    public List<QuizQuestion> getQuestionsCreatedByUser(long userId) {
        List<QuizQuestion> questions = new ArrayList<>();
        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("Select * from QuizQuestions where user=" + userId)) {
            while (rs.next()) {
                List<QuizAnswer> answers = new ArrayList<>(4);
                answers.add(new QuizAnswer(rs.getString(QuizModule.RIGHT_ANSWER), QuizModule.RIGHT_ANSWER));
                answers.add(new QuizAnswer(rs.getString(QuizModule.WRONG_ANSWER_1), QuizModule.WRONG_ANSWER_1));
                answers.add(new QuizAnswer(rs.getString(QuizModule.WRONG_ANSWER_2), QuizModule.WRONG_ANSWER_2));
                answers.add(new QuizAnswer(rs.getString(QuizModule.WRONG_ANSWER_3), QuizModule.WRONG_ANSWER_3));

                QuizQuestion question = new QuizQuestion(
                        rs.getInt("id"),
                        rs.getString("text"),
                        answers,
                        rs.getString("user")
                );
                questions.add(question);
            }
        } catch (SQLException e) {
            throw new TapirException("Could not get questions of user " + userId, e);
        }

        return questions;
    }

    public Set<Long> getKnownUsers() {
        return knownUsers;
    }

    public void deleteQuestionWhereLike(String whereLike) {
        try (Statement statement = getConnection().createStatement();) {
            statement.executeUpdate("delete from QuizQuestions where text like '%" + whereLike + "%'");
        } catch (SQLException e) {
            throw new TapirException("Could not delete questions where like " + whereLike, e);
        }
    }

    public Map<String, String> getUserInfoById(long id) {
        Map<String, String> userInfo = new HashMap<>(2);
        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("Select * from User where id='" + id + "'")) {
            while (rs.next()) {
                userInfo.put("name", rs.getString("name"));
                userInfo.put("id", rs.getLong("id") + "");
            }
        } catch (SQLException e) {
            throw new TapirException("Could not get info for user " + id, e);
        }
        return userInfo;
    }

    public Map<String, String> getUserInfoByName(String name) {
        Map<String, String> userInfo = new HashMap<>(2);
        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("Select * from User where name='" + name + "'")) {
            while (rs.next()) {
                userInfo.put("name", rs.getString("name"));
                userInfo.put("id", rs.getLong("id") + "");
            }
        } catch (SQLException e) {
            throw new TapirException("Could not get info for user " + name, e);
        }
        return userInfo;
    }

    public void registerPokemon(User user, Pokemon pokemon) {
        registerPokemon(user, List.of(pokemon));
    }

    public void registerPokemon(User user, List<Pokemon> pokemonList) {
        try (final PreparedStatement preparedStatement = getConnection()
                .prepareStatement("insert into Pokemons(user, dexIndex, name, level) values (?,?,?,?)")) {
            for (Pokemon pokemon : pokemonList) {
                preparedStatement.setLong(1, user.getIdLong());
                preparedStatement.setInt(2, pokemon.getPokedexIndex());
                preparedStatement.setString(3, pokemon.getName());
                preparedStatement.setInt(4, pokemon.getLevel());
                preparedStatement.addBatch();
            }

            preparedStatement.executeUpdate();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public List<Pokemon> getPokemonOfUser(User user) {
        return getPokemonOfUser(user.getIdLong());
    }

    public List<Pokemon> getPokemonOfUser(Long id) {
        List<Pokemon> pokemonList = new ArrayList<>();

        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery("select rowid, * from Pokemons where user=" + id)) {
            while (rs.next()) {
                Pokemon pokemon = new Pokemon(rs.getInt("rowid"), rs.getInt("dexIndex"), rs.getString("name"),
                        rs.getInt("level"));
                pokemonList.add(pokemon);
            }
        } catch (SQLException e) {
            throw new TapirException("Could not read Users!", e);
        }

        Collections.sort(pokemonList);
        return pokemonList;
    }

    public void removePokemonFromUser(List<Pokemon> pokemonToRemove) {
        try (final PreparedStatement preparedStatement =
                     getConnection().prepareStatement("delete from Pokemons where rowid=?")) {
            for (Pokemon pokemon : pokemonToRemove) {
                preparedStatement.setInt(1, pokemon.getRowid());
                preparedStatement.addBatch();
            }
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Couldnt Remove Pokemon", e);
        }
    }
}
