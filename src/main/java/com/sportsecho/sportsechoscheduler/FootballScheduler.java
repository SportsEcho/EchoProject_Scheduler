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
import java.time.temporal.TemporalAdjusters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Slf4j
@Component
@RequiredArgsConstructor
public class FootballScheduler {

    private final S3FileService s3FileService;
    private final JdbcTemplate jdbcTemplate;
    @Value("${api.key}")
    private String apiKey;

    @Scheduled(cron = "0 12,24,36,48 * * * ?")
    public void updateTodayFootballGames() throws IOException, InterruptedException, JSONException {

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String todayString = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String season = SchedulerUtils.calculateSeason("EPL", today);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(
                "https://api-football-v1.p.rapidapi.com/v3/fixtures?date=" + todayString
                    + "&league=39&season=" + season + "&timezone=Asia%2FSeoul"))
            .header("X-RapidAPI-Key", apiKey)
            .header("X-RapidAPI-Host", "api-football-v1.p.rapidapi.com")
            .method("GET", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        String jsonData = response.body();
        JSONObject jsonObject = new JSONObject(jsonData);

        if (jsonObject.getInt("results") == 0) {
            log.info("축구 경기 일정이 없습니다");
            return;
        }

        JSONArray gameList = jsonObject.getJSONArray("response");
        log.info(
            "=============================================== EPL 경기 업데이트 ===============================================");
        log.info(gameList.toString());

        for (int i = 0; i < gameList.length(); i++) {
            JSONObject teams = gameList.getJSONObject(i).getJSONObject("teams");
            JSONObject goals = gameList.getJSONObject(i).getJSONObject("goals");

            String homeTeamName = teams.getJSONObject("home").getString("name");
            String awayTeamName = teams.getJSONObject("away").getString("name");
            // 이미 시작하지 않은 경기는 null값을 주고 있으므로 0으로 처리
            int homeScore = goals.isNull("home") ? 0 : goals.getInt("home");
            int awayScore = goals.isNull("away") ? 0 : goals.getInt("away");

            String updateQuery = "UPDATE game SET away_goal = ?, home_goal = ?, modified_at = NOW() WHERE date_format(date, '%Y-%m-%d') = ? AND home_team_name = ? AND away_team_name = ?";
            jdbcTemplate.update(updateQuery, awayScore, homeScore, todayString, homeTeamName,
                awayTeamName);


        }

    }

    @Scheduled(cron = "0 0 0 1 * ?") // 월 단위로 진행 자정에 실행
    public void fetchUpcomingFootballGames() throws IOException, InterruptedException {
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate firstDayOfMonth = now.withDayOfMonth(1); // 해당 월의 첫째 날
        LocalDate lastDayOfMonth = now.with(TemporalAdjusters.lastDayOfMonth()); // 해당 월의 마지막 날
        String season = SchedulerUtils.calculateSeason("EPL", now);

        String from = firstDayOfMonth.format(DateTimeFormatter.ISO_DATE);
        String to = lastDayOfMonth.format(DateTimeFormatter.ISO_DATE);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(
                "https://api-football-v1.p.rapidapi.com/v3/fixtures?&league=39" +
                    "&season=" + season +
                    "&from=" + from + "&to=" + to +
                    "&timezone=Asia%2FSeoul"))
            .header("X-RapidAPI-Key", apiKey)
            .header("X-RapidAPI-Host", "api-football-v1.p.rapidapi.com")
            .method("GET", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        String jsonData = response.body();
        JSONObject jsonObject = new JSONObject(jsonData);

        JSONArray gameList = jsonObject.getJSONArray("response");
        log.info(
            "=============================================== 축구 경기 추가 ===============================================");
        log.info(gameList.toString());

        for (int i = 0; i < gameList.length(); i++) {
            JSONObject fixture = gameList.getJSONObject(i).getJSONObject("fixture");
            JSONObject teams = gameList.getJSONObject(i).getJSONObject("teams");
            JSONObject league = gameList.getJSONObject(i).getJSONObject("league");
            JSONObject venue = fixture.getJSONObject("venue");
            JSONObject goals = gameList.getJSONObject(i).getJSONObject("goals");

            String homeTeamName = teams.getJSONObject("home").getString("name");
            String awayTeamName = teams.getJSONObject("away").getString("name");
            String homeTeamLogo = teams.getJSONObject("home").getString("logo");
            String awayTeamLogo = teams.getJSONObject("away").getString("logo");
            String leagueLogo = league.getString("logo");

            String dateString = fixture.getString("date");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
            LocalDateTime localDateTime = LocalDateTime.parse(dateString, formatter);

            String venueName = venue.getString("name");
            // 이미 시작하지 않은 경기는 null값을 주고 있으므로 0으로 처리
            int homeScore = goals.isNull("home") ? 0 : goals.getInt("home");
            int awayScore = goals.isNull("away") ? 0 : goals.getInt("away");
            int sportsType = 0; // sportsType은 정수 값으로

            String insertQuery =
                "INSERT INTO game (away_goal, home_goal, sports_type, created_at, date, modified_at, away_team_logo, away_team_name, home_team_logo, home_team_name, league_logo, venue_name) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(insertQuery,
                awayScore, homeScore, sportsType,
                LocalDateTime.now(), localDateTime, LocalDateTime.now(),
                awayTeamLogo, awayTeamName, homeTeamLogo,
                homeTeamName, leagueLogo, venueName);

        }
    }

    //    @Scheduled(cron = "0 0 * * * ?")


}
