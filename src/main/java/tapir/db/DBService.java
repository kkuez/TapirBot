package tapir.db;

import tapir.Main;
import tapir.UserWrapper;
import tapir.db.entities.QuestionAttachmentEntity;
import tapir.db.entities.QuizQuestionEntity;
import net.dv8tion.jda.api.entities.User;
import tapir.db.entities.UserQuizQuestionEntity;
import tapir.db.factories.QuestionAttachmentFactory;
import tapir.db.factories.QuestionFactory;
import tapir.db.factories.UserQuizQuestionFactory;
import tapir.exception.TapirException;
import tapir.pokemon.Pokemon;
import tapir.quiz.QuestionAttachment;
import tapir.quiz.QuizModule;
import tapir.quiz.QuizAnswer;
import tapir.quiz.QuizQuestion;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.persistence.*;

public class DBService {

    private final String dbPath;
    private EntityManagerFactory emf;

    private Set<Long> knownUsers;

    public DBService(Properties properties) {
        final boolean isDev = Main.isDev();
        if (isDev) {
            emf = Persistence.createEntityManagerFactory("dev");
        } else {
            emf = Persistence.createEntityManagerFactory("prod");
        }
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
        //TODO User aus dem Result nehmen (siehe Object[])
        final List<Object[]> questionEntitiesUntyped =
                emf.createEntityManager()
                        .createQuery("from QuizQuestionEntity qq inner join UserEntity u on " +
                "u.id=qq.user where not exists (from UserQuizQuestionEntity uqq where qq.id=uqq.question " +
                "and uqq.user=" + userId + ") and not qq.user=" + userId)
                        .getResultList();

        List<QuizQuestion> questions = new ArrayList<>(questionEntitiesUntyped.size());
        for (Object[] o : questionEntitiesUntyped) {
            final QuizQuestion question = QuestionFactory.createPojo(this, (QuizQuestionEntity) o[0]);
            questions.add(question);
        }
        return questions;
    }

    private List<QuestionAttachmentEntity> getQuestionAttachmentsEntities(int questionId) {
        final EntityManager em = emf.createEntityManager();
        final List<QuestionAttachmentEntity> resultList;
        try{
            // Instead of table name, one has to select from the java class name
            // https://stackoverflow.com/questions/9954590/hibernate-error-querysyntaxexception-users-is-not-mapped-from-users
            resultList = em.createQuery("FROM QuestionAttachmentEntity where question=" + questionId,
                    QuestionAttachmentEntity.class).getResultList();
        } finally {
            em.close();
        }
        return resultList;
    }

    public List<QuestionAttachment> getQuestionAttachments(int questionId) {
            return getQuestionAttachmentsEntities(questionId).stream()
                    .map(QuestionAttachmentFactory::createPojo).collect(Collectors.toList());
    }

    private long getUserId(User user) {
        return user.getIdLong();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public void sendAnswer(long userId, int questionId, String answer) {
        UserQuizQuestionEntity entity = UserQuizQuestionFactory.createEntity(userId, questionId, answer);
        final EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(entity);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    public List<QuizModule.RankingTableEntry> getUserScoresPointRated() {
        List<QuizModule.RankingTableEntry> rankingTable = new ArrayList<>();

        final String sql = "select user as userId, (select name from User where id=uuq.user) as userName, (select sum(IIF(answer ='Right_Answer', 3, IIF(answer = 'Keine Ahnung!', 0, -2))) from User_QuizQuestions where user=uuq.user) as points, count(question) as answered, (select Count(*) from QuizQuestions qq WHERE qq.user=uuq.user) as created from User_QuizQuestions uuq group by user order by points asc";

        try (Statement statement = getConnection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                final QuizModule.RankingTableEntry rankingTableEntry =
                        new QuizModule.RankingTableEntry(
                                rs.getLong("userId"),
                                rs.getString("userName"),
                                rs.getInt("points"),
                                rs.getInt("answered"),
                                rs.getInt("created"));
                rankingTable.add(rankingTableEntry);
            }
        } catch (SQLException e) {
            throw new TapirException("Could not get UserScores!", e);
        }
        rankingTable.sort(Comparator.comparing(entry -> entry.getPoints() + entry.getCreated()));
        Collections.reverse(rankingTable);
        return rankingTable;
    }

    public void enterQuestion(User user, QuizQuestion question, List<QuizAnswer> answers, String explaination) {
        final QuizQuestionEntity quizQuestionEntity = new QuizQuestionEntity();
        quizQuestionEntity.setText(question.getText());
        quizQuestionEntity.setRight_Answer(answers.get(0).getText());
        quizQuestionEntity.setWrong_Answer_1(answers.get(1).getText());
        quizQuestionEntity.setWrong_Answer_2(answers.get(2).getText());
        quizQuestionEntity.setWrong_Answer_3(answers.get(3).getText());
        quizQuestionEntity.setExplaination(explaination);
        quizQuestionEntity.setUser(user.getIdLong());

        // Persist Question
        final EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(quizQuestionEntity);
            em.getTransaction().commit();
        } finally {
            em.close();
        }

        // Persist QuestionAttachments
        final EntityManager emAttachments = emf.createEntityManager();
        try {
            question.getAttachments().forEach(a->a.setQuestionId(Optional.of(quizQuestionEntity.getId())));
            final List<QuestionAttachmentEntity> entities = question.getAttachments().stream().map(QuestionAttachmentFactory::createEntity).collect(Collectors.toList());
            emAttachments.getTransaction().begin();
            for (QuestionAttachmentEntity questionAttachmentEntity : entities) {
                emAttachments.persist(questionAttachmentEntity);
            }
            emAttachments.getTransaction().commit();
        } finally {
            emAttachments.close();
        }
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

            final int[] rows = preparedStatement.executeBatch();
            if (rows.length != pokemonList.size()) {
                throw new RuntimeException("Not all pokemons inserted!");
            }
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
            final int[] rows = preparedStatement.executeBatch();
            if (rows.length != pokemonToRemove.size()) {
                throw new RuntimeException("Not all pokemons removed!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Couldnt Remove Pokemon", e);
        }
    }

    public void registerNewOrden(User user) {
        try (Statement statement = getConnection().createStatement();) {
            statement.executeUpdate(
                    "insert into User_Ordencount(user) values(" + user.getIdLong() + ")");
        } catch (SQLException e) {
            throw new TapirException("Could not set Orden for user " + user.getName() + " " + user.getIdLong(), e);
        }
        try (Statement statement = getConnection().createStatement();) {
            statement.executeUpdate("delete from Pokemons where user=" + user.getIdLong());
        } catch (SQLException e) {
            throw new TapirException("Could not remove Pokemon for user " + user.getName() + " " + user.getIdLong(), e);
        }
    }

    public int getOrdenCount(Long userId) {
        int count = 0;
        try (Statement statement = getConnection().createStatement();
             ResultSet rs =
                     statement.executeQuery("select count(*) as count from User_Ordencount where user="
                             + userId)) {
            while (rs.next()) {
                count = rs.getInt("count");
            }
        } catch (SQLException e) {
            throw new TapirException("Could not get count of orden  for user " + userId, e);
        }
        return count;
    }

    private QuizQuestionEntity getQuestionEntityById(int id, EntityManager em) {
        return em.find(QuizQuestionEntity.class, id);
    }

    private QuizQuestionEntity getQuestionEntityById(int id) {
        final EntityManager em = emf.createEntityManager();
        final QuizQuestionEntity questionById;
        try {
            questionById = getQuestionEntityById(id, em);
        } finally {
            em.close();
        }
        return questionById;
    }

    public void addQuestionAttachments(List<QuestionAttachment> attachments, Optional<Integer> questionIdOpt) {
        final List<QuestionAttachmentEntity> entities = attachments.stream().map(QuestionAttachmentFactory::createEntity).collect(Collectors.toList());
        final EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            final int questionId;
            if(questionIdOpt.isPresent()) {
                questionId = questionIdOpt.get();
            } else {
                questionId = 999999;
            }
            entities.forEach(questionAttachmentEntity -> questionAttachmentEntity.setQuestion(questionId));
            entities.forEach(em::persist);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    public void updateQuestion(QuizQuestion question) {
        final QuizQuestionEntity questionEntity = getQuestionEntityById(question.getId());
        questionEntity.setExplaination(question.getExplaination());
        questionEntity.setText(question.getText());
        questionEntity.setRight_Answer(question.getAnswers().get(0).getText());
        questionEntity.setWrong_Answer_1(question.getAnswers().get(1).getText());
        questionEntity.setWrong_Answer_2(question.getAnswers().get(2).getText());
        questionEntity.setWrong_Answer_3(question.getAnswers().get(3).getText());
        final EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(questionEntity);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }
}
