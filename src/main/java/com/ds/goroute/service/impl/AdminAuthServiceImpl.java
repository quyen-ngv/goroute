package com.ds.goroute.service.impl;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AdminLoginRequest;
import com.ds.goroute.dto.request.CreateHostOrganizationRequest;
import com.ds.goroute.dto.request.PartnerRegisterRequest;
import com.ds.goroute.entity.User;
import com.ds.goroute.service.HostOrganizationService;
import com.ds.goroute.type.AccountStatus;
import com.ds.goroute.type.AuthProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
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
 private final HostOrganizationService organizations;

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
  return issueSession(user,admin,partner);
 }

 /**
  * Self-serve partner sign-up. The account is created ACTIVE with the chosen password (no temporary
  * password round-trip, unlike admin provisioning) and immediately owns one UNVERIFIED organization,
  * which is what makes it a partner for {@code AdminMapper.isPartnerUser} and the JWT filter.
  */
 @Override @Transactional public AuthResponse partnerRegister(PartnerRegisterRequest request){
  String username=request.getUsername().trim(); String email=request.getEmail().trim().toLowerCase();
  if(users.findByUsername(username).isPresent())throw new BusinessException(ErrorConstant.BAD_REQUEST,"Username is already taken");
  if(users.findByEmailIncludingDeleted(email).isPresent())throw new BusinessException(ErrorConstant.BAD_REQUEST,"Email is already registered");
  User user=User.builder().id(UUID.randomUUID()).username(username).email(email).fullName(request.getFullName().trim())
          .passwordHash(encoder.encode(request.getPassword())).provider(AuthProvider.LOCAL)
          .defaultCurrency("VND").defaultTravelMode("driving").language("vi").theme("system")
          .onboardingCompleted(false).mustChangePassword(false).accountStatus(AccountStatus.ACTIVE.name()).build();
  try{users.insert(user);}
  catch(DataIntegrityViolationException ex){throw new BusinessException(ErrorConstant.BAD_REQUEST,"Username or email already exists");}
  CreateHostOrganizationRequest organization=new CreateHostOrganizationRequest();
  organization.setLegalName(request.getLegalName()); organization.setDisplayName(request.getDisplayName());
  organization.setOrganizationType(request.getOrganizationType()); organization.setTimezone(request.getTimezone());
  organization.setContactEmail(email); organization.setContactPhone(request.getContactPhone());
  organizations.create(user.getId(),organization);
  return issueSession(user,false,true);
 }

 private AuthResponse issueSession(User user,boolean admin,boolean partner){
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
