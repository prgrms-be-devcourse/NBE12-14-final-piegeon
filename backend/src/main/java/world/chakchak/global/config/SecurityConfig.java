package world.chakchak.global.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      SecurityContextRepository securityContextRepository,
      AuthenticationEntryPoint authenticationEntryPoint,
      AccessDeniedHandler accessDeniedHandler)
      throws Exception {
    return http
        // CSRF 토큰 대신 SameSite=Lax 쿠키와 CORS 허용 origin 제한으로 막는다. (팀 결정 필요 항목)
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        // 로그인·로그아웃은 AuthController가 JSON API로 직접 처리한다.
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        // SessionAuthenticator가 로그인 정보를 저장하는 저장소와 필터가 읽는 저장소를 같은 빈으로 맞춘다.
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login")
                .permitAll()
                .requestMatchers("/actuator/health", "/actuator/prometheus")
                .permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                .permitAll()
                .anyRequest()
                .authenticated())
        .exceptionHandling(ex ->
            ex.authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(corsProperties.allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    // 쿠키를 주고받으려면 필요하다. 이 설정에서는 allowedOrigins에 "*"를 쓸 수 없다.
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
