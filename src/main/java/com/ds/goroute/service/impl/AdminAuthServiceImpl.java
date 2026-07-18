package com.ds.goroute.service.impl;
import com.ds.goroute.dto.request.AdminLoginRequest;
import com.ds.goroute.dto.response.AuthResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.AdminAuthService;
import com.ds.goroute.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Map;
import static com.ds.goroute.constant.ErrorConstant.UNAUTHORIZED;
@Service @RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {
 private final UserRepository users; private final PasswordEncoder encoder; private final JwtUtils jwt;
 public AuthResponse login(AdminLoginRequest request){var user=users.findByUsername(request.getUsername()).or(()->users.findByEmail(request.getUsername())).orElseThrow(()->new BusinessException(UNAUTHORIZED,"Invalid username or password"));if(!encoder.matches(request.getPassword(),user.getPasswordHash()))throw new BusinessException(UNAUTHORIZED,"Invalid username or password");String token=jwt.generateToken(Map.of("userId",user.getId().toString(),"email",user.getEmail(),"admin",true),user.getId().toString());return AuthResponse.builder().accessToken(token).user(UserResponse.builder().id(user.getId()).email(user.getEmail()).username(user.getUsername()).fullName(user.getFullName()).build()).build();
 }
}
