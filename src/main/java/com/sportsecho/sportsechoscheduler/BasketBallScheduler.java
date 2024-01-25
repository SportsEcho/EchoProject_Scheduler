package com.sportsecho.sportsechoscheduler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BasketBallScheduler {

    private final S3FileService s3FileService;
    private final JdbcTemplate jdbcTemplate;
    @Value("${api.key}")
    private String apiKey;
    private final String filename = "basketball.txt";
    @Scheduled(cron = "0 12,24,36,48 * * * ?")
    public void updateTodayNbaGames() throws IOException, InterruptedException, JSONException {
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String season = SchedulerUtils.calculateSeason("NBA", now);

        String todayString = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(
                "https://api-basketball.p.rapidapi.com/games?timezone=Asia%2FSeoul&" +
                    "season=" + season +
                    "&league=12&date=" + todayString))
            .header("X-RapidAPI-Key", apiKey)
            .header("X-RapidAPI-Host", "api-basketball.p.rapidapi.com")
            .method("GET", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        String jsonData = response.body();
        JSONObject jsonObject = new JSONObject(jsonData);

        if (jsonObject.getInt("results") == 0) {
            log.info("농구 경기 일정이 없습니다");
            return;
        }

        JSONArray gameList = jsonObject.getJSONArray("response");
        log.info(
            "=============================================== NBA 경기 업데이트 ===============================================");
        log.info(gameList.toString());

        for (int i = 0; i < gameList.length(); i++) {
            JSONObject game = gameList.getJSONObject(i);
            JSONObject teams = game.getJSONObject("teams");
            JSONObject scores = game.getJSONObject("scores");

            // 이미 시작하지 않은 경기는 null값을 주고 있으므로 0으로 처리
            int homeScore = scores.getJSONObject("home").isNull("total") ? 0
                : scores.getJSONObject("home").getInt("total");
            int awayScore = scores.getJSONObject("away").isNull("total") ? 0
                : scores.getJSONObject("away").getInt("total");
            String homeTeamName = teams.getJSONObject("home").getString("name");
            String awayTeamName = teams.getJSONObject("away").getString("name");

            String updateQuery = "UPDATE game SET away_goal = ?, home_goal = ?, modified_at = NOW() WHERE date_format(date, '%Y-%m-%d') = ? AND home_team_name = ? AND away_team_name = ?";
            jdbcTemplate.update(updateQuery, awayScore, homeScore, todayString, homeTeamName,
                awayTeamName);
        }
    }
    @Scheduled(cron = "0 0 0,6,12,18 * * ?")
    public void fetchingUpcomingNBAGames() throws IOException, InterruptedException, JSONException {
        String scheduleDay = s3FileService.getS3FileContent(filename);
        log.info(scheduleDay + "날짜의 NBA 경기를 가져옵니다");
        LocalDate localDate = LocalDate.parse(scheduleDay, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String season = SchedulerUtils.calculateSeason("NBA", localDate);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(
                "https://api-basketball.p.rapidapi.com/games?timezone=Asia%2FSeoul&" +
                    "season=" + season +
                    "&league=12&date=" + scheduleDay))
            .header("X-RapidAPI-Key", apiKey)
            .header("X-RapidAPI-Host", "api-basketball.p.rapidapi.com")
            .method("GET", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        String jsonData = response.body();
        JSONObject jsonObject = new JSONObject(jsonData);

        JSONArray gameList = jsonObject.getJSONArray("response");
        log.info(
            "=============================================== NBA 경기 추가 ===============================================");
        log.info(gameList.toString());

        for (int i = 0; i < gameList.length(); i++) {
            JSONObject game = gameList.getJSONObject(i);
            JSONObject teams = game.getJSONObject("teams");
            JSONObject scores = game.getJSONObject("scores");
            JSONObject league = game.getJSONObject("league");
            JSONObject venue = game.getJSONObject("country");

            String homeTeamName = teams.getJSONObject("home").getString("name");
            String awayTeamName = teams.getJSONObject("away").getString("name");
            String homeTeamLogo = teams.getJSONObject("home").getString("logo");
            String awayTeamLogo = teams.getJSONObject("away").getString("logo");
            String leagueLogo = league.getString("logo");

            String venueName = venue.getString("name");

            String dateString = game.getString("date");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
            LocalDateTime localDateTime = LocalDateTime.parse(dateString, formatter);

            // 이미 시작하지 않은 경기는 null값을 주고 있으므로 0으로 처리
            int homeScore = scores.getJSONObject("home").isNull("total") ? 0 : scores.getJSONObject("home").getInt("total");
            int awayScore = scores.getJSONObject("away").isNull("total") ? 0 : scores.getJSONObject("away").getInt("total");
            int sportsType = 1; // sportsType은 정수 값으로

            String insertQuery =
                "INSERT INTO game (away_goal, home_goal, sports_type, created_at, date, modified_at, away_team_logo, away_team_name, home_team_logo, home_team_name, league_logo, venue_name) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(insertQuery,
                awayScore, homeScore, sportsType,
                LocalDateTime.now(), localDateTime, LocalDateTime.now(),
                awayTeamLogo, awayTeamName, homeTeamLogo,
                homeTeamName, leagueLogo, venueName);

            s3FileService.deleteFile(filename);
            s3FileService.uploadFile(filename, localDate.plusDays(1).toString());
        }
    }

}
