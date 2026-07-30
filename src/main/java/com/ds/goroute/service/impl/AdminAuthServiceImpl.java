package com.ds.goroute.service.impl;
import com.ds.goroute.dto.request.AdminLoginRequest;
import com.ds.goroute.dto.response.AuthResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.dto.response.PortalSessionResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.AdminAuthService;
import com.ds.goroute.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.ds.goroute.constant.ErrorConstant.UNAUTHORIZED;
@Service @RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {
 private final UserRepository users; private final AdminMapper adminMapper; private final PasswordEncoder encoder; private final JwtUtils jwt;

 public AuthResponse login(AdminLoginRequest request){
  var user=users.findByUsername(request.getUsername()).or(()->users.findByEmail(request.getUsername()))
          .orElseThrow(()->new BusinessException(UNAUTHORIZED,"Invalid username or password"));
  if(user.getPasswordHash()==null||!encoder.matches(request.getPassword(),user.getPasswordHash()))
      throw new BusinessException(UNAUTHORIZED,"Invalid username or password");
  if("LOCKED".equals(user.getAccountStatus())||"DISABLED".equals(user.getAccountStatus()))
      throw new BusinessException(UNAUTHORIZED,"Account is not active");
  boolean admin=adminMapper.hasAnyRole(user.getId());
  boolean partner=adminMapper.isPartnerUser(user.getId());
  if(!admin&&!partner)throw new BusinessException(UNAUTHORIZED,"This account does not have portal access");
  Map<String,Object> claims=new HashMap<>();claims.put("userId",user.getId().toString());claims.put("email",user.getEmail());
  claims.put("admin",admin);claims.put("partner",partner);claims.put("mustChangePassword",Boolean.TRUE.equals(user.getMustChangePassword()));
  String token=jwt.generateToken(claims,user.getId().toString());users.updateLastLoginAt(user.getId());
  return AuthResponse.builder().accessToken(token).user(userResponse(user)).build();
 }

 @Override public PortalSessionResponse session(UUID userId){
  var user=users.findById(userId).orElseThrow(()->new BusinessException(UNAUTHORIZED,"Account not found"));
  List<String> roles=adminMapper.findRoleCodes(userId);List<String> permissions=adminMapper.findPermissionCodes(userId);
  return PortalSessionResponse.builder().user(userResponse(user)).admin(!roles.isEmpty()).partner(adminMapper.isPartnerUser(userId))
          .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword())).roles(roles).permissions(permissions).build();
 }

 private UserResponse userResponse(com.ds.goroute.entity.User user){return UserResponse.builder().id(user.getId()).email(user.getEmail())
         .username(user.getUsername()).fullName(user.getFullName()).avatarUrl(user.getAvatarUrl())
         .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword())).accountStatus(user.getAccountStatus()).build();}
}
