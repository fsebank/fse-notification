package com.fse.fsenotification.security;

import com.fse.fsenotification.accesskey.AccessKeyService;
import com.fse.fsenotification.accesskey.BalanceService;
import com.fse.fsenotification.common.InsufficientBalanceException;
import com.fse.fsenotification.modules.message.service.MessagePriceService;
import com.fse.fsenotification.modules.message.service.MessageService;
import com.fse.fsenotification.admin.AdminUserDetails;
import com.fse.fsenotification.admin.AdminUserDetailsService;
import com.fse.fsenotification.admin.AdminUserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({SecurityConfig.class, JwtConfig.class, AdminTokenService.class, LoginAttemptService.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminTokenService adminTokenService;

    @MockitoBean
    private AccessKeyService accessKeyService;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @MockitoBean
    private BalanceService balanceService;

    @MockitoBean
    private MessageService messageService;

    @MockitoBean
    private MessagePriceService messagePriceService;

    @Test
    void partnerRequestWithoutHeadersIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/partner/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing X-Client-Id or X-Client-Secret header"));
    }

    @Test
    void partnerRequestWithValidKeyIsAccepted() throws Exception {
        given(accessKeyService.validate(eq("fse_abc"), eq("fsk_secret"), anyString()))
                .willReturn(new PartnerPrincipal(1L, "fse_abc", "Acme Ltd"));

        mockMvc.perform(get("/api/v1/partner/me")
                        .header("X-Client-Id", "fse_abc")
                        .header("X-Client-Secret", "fsk_secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Acme Ltd"));
    }

    @Test
    void partnerRequestFromNonWhitelistedIpIsForbidden() throws Exception {
        given(accessKeyService.validate(anyString(), anyString(), anyString()))
                .willThrow(new PartnerAuthenticationException(HttpStatus.FORBIDDEN,
                        "Request IP address is not whitelisted"));

        mockMvc.perform(get("/api/v1/partner/me")
                        .header("X-Client-Id", "fse_abc")
                        .header("X-Client-Secret", "fsk_secret"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Request IP address is not whitelisted"));
    }

    @Test
    void partnerKeyCannotAccessAdminApis() throws Exception {
        mockMvc.perform(get("/api/v1/admin/access-keys")
                        .header("X-Client-Id", "fse_abc")
                        .header("X-Client-Secret", "fsk_secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanLoginAndUseTokenToListAccessKeys() throws Exception {
        givenAdmin(true);
        given(accessKeyService.findAll()).willReturn(List.of());

        String body = mockMvc.perform(post(SecurityConfig.ADMIN_LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"S3cure-Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/v1/admin/access-keys").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminLoginWithWrongPasswordIsRejected() throws Exception {
        givenAdmin(true);

        mockMvc.perform(post(SecurityConfig.ADMIN_LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginIgnoresStaleBearerHeader() throws Exception {
        givenAdmin(true);

        mockMvc.perform(post(SecurityConfig.ADMIN_LOGIN_PATH)
                        .header("Authorization", "Bearer expired.or.invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"S3cure-Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void adminApisRequireToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/access-keys"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/access-keys").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenStopsWorkingOnceAdminIsDisabled() throws Exception {
        String token = adminTokenService.issue("admin").value();
        givenAdmin(false);

        mockMvc.perform(get("/api/v1/admin/access-keys").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanDeleteAccessKey() throws Exception {
        givenAdmin(true);
        String token = adminTokenService.issue("admin").value();

        mockMvc.perform(delete("/api/v1/admin/access-keys/7").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        verify(accessKeyService).delete(7L, "admin");
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception {
        given(adminUserDetailsService.loadUserByUsername("target")).willReturn(adminDetails("target", true,
                Roles.ADMIN, null));
        String badLogin = "{\"username\":\"target\",\"password\":\"wrong\"}";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(SecurityConfig.ADMIN_LOGIN_PATH).contentType(MediaType.APPLICATION_JSON)
                            .content(badLogin))
                    .andExpect(status().isUnauthorized());
        }
        // Even the correct password is refused while locked out.
        mockMvc.perform(post(SecurityConfig.ADMIN_LOGIN_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"target\",\"password\":\"S3cure-Passw0rd!\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void adminWithPendingPasswordChangeCanOnlyChangePassword() throws Exception {
        given(adminUserDetailsService.loadUserByUsername("admin")).willReturn(adminDetails("admin", true,
                Roles.PASSWORD_CHANGE_REQUIRED, null));

        String body = mockMvc.perform(post(SecurityConfig.ADMIN_LOGIN_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"S3cure-Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/v1/admin/access-keys").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(startsWith("Password change required")));

        mockMvc.perform(post(SecurityConfig.ADMIN_CHANGE_PASSWORD_PATH).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"S3cure-Passw0rd!\",\"newPassword\":\"An0ther-Str0ng-Pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
        verify(adminUserService).changeOwnPassword("admin", "S3cure-Passw0rd!", "An0ther-Str0ng-Pass");
    }

    @Test
    void tokenIssuedBeforePasswordChangeIsRejected() throws Exception {
        String token = adminTokenService.issue("admin").value();
        given(adminUserDetailsService.loadUserByUsername("admin")).willReturn(adminDetails("admin", true,
                Roles.ADMIN, Instant.now().plusSeconds(5)));

        mockMvc.perform(get("/api/v1/admin/access-keys").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanUpdateMessagePrice() throws Exception {
        givenAdmin(true);
        String token = adminTokenService.issue("admin").value();

        mockMvc.perform(put("/api/v1/admin/message-prices/1").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unitPrice\":5.50}"))
                .andExpect(status().isOk());
        verify(messagePriceService).update(eq(1L), any(), eq("admin"));
    }

    @Test
    void negativeMessagePriceIsRejected() throws Exception {
        givenAdmin(true);
        String token = adminTokenService.issue("admin").value();

        mockMvc.perform(post("/api/v1/admin/message-prices").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"SMS\",\"unitPrice\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void partnerKeyCannotManageMessagePrices() throws Exception {
        givenPartner();

        mockMvc.perform(get("/api/v1/admin/message-prices")
                        .header("X-Client-Id", "fse_abc")
                        .header("X-Client-Secret", "fsk_secret"))
                .andExpect(status().isUnauthorized());
    }

    private void givenAdmin(boolean enabled) {
        given(adminUserDetailsService.loadUserByUsername("admin"))
                .willReturn(adminDetails("admin", enabled, Roles.ADMIN, null));
    }

    private AdminUserDetails adminDetails(String username, boolean enabled, String role, Instant passwordChangedAt) {
        return new AdminUserDetails(username, passwordEncoder.encode("S3cure-Passw0rd!"), enabled,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), passwordChangedAt);
    }

    private static final String SMS_BODY = "{\"recipients\":[\"08012345678\"],\"message\":\"Hello\"}";

    @Test
    void sendSmsRequiresPartnerHeadersNotBearerToken() throws Exception {
        givenAdmin(true);
        String adminToken = adminTokenService.issue("admin").value();

        mockMvc.perform(post("/api/v1/messages/sms").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(SMS_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing X-Client-Id or X-Client-Secret header"));
    }

    @Test
    void partnerCanSendSms() throws Exception {
        givenPartner();

        mockMvc.perform(post("/api/v1/messages/sms").header("X-Client-Id", "fse_abc")
                        .header("X-Client-Secret", "fsk_secret")
                        .contentType(MediaType.APPLICATION_JSON).content(SMS_BODY))
                .andExpect(status().isCreated());
        verify(messageService).sendSms(any(), any());
    }

    @Test
    void sendWithInsufficientBalanceReturns402() throws Exception {
        givenPartner();
        given(messageService.sendSms(any(), any())).willThrow(
                new InsufficientBalanceException("Insufficient balance: required 4.00 NGN, available 0.00 NGN"));

        mockMvc.perform(post("/api/v1/messages/sms").header("X-Client-Id", "fse_abc")
                        .header("X-Client-Secret", "fsk_secret")
                        .contentType(MediaType.APPLICATION_JSON).content(SMS_BODY))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").value(startsWith("Insufficient balance")));
    }

    @Test
    void invalidSmsRequestIsRejected() throws Exception {
        givenPartner();

        mockMvc.perform(post("/api/v1/messages/sms").header("X-Client-Id", "fse_abc")
                        .header("X-Client-Secret", "fsk_secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipients\":[],\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private void givenPartner() {
        given(accessKeyService.validate(eq("fse_abc"), eq("fsk_secret"), anyString()))
                .willReturn(new PartnerPrincipal(1L, "fse_abc", "Acme Ltd"));
    }

    @Test
    void unmappedPathsOutsideApiAreDenied() throws Exception {
        mockMvc.perform(get("/anything"))
                .andExpect(status().isUnauthorized());
    }
}
