package fi.hmp.memorygame.web.rest;

import com.codahale.metrics.annotation.Timed;

import fi.hmp.memorygame.domain.Authority;
import fi.hmp.memorygame.domain.PersistentToken;
import fi.hmp.memorygame.domain.User;
import fi.hmp.memorygame.repository.PersistentTokenRepository;
import fi.hmp.memorygame.repository.UserRepository;
import fi.hmp.memorygame.security.SecurityUtils;
import fi.hmp.memorygame.service.MailService;
import fi.hmp.memorygame.service.UserService;
import fi.hmp.memorygame.web.rest.dto.UserDTO;

import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/api")
public class AccountResource {

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);

    @Inject
    private UserRepository userRepository;

    @Inject
    private UserService userService;

    @Inject
    private PersistentTokenRepository persistentTokenRepository;

    @Inject
    private MailService mailService;

    /**
     * POST  /register -> register the user.
     */
    @RequestMapping(value = "/register",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<JSONObject> registerAccount(@Valid @RequestBody UserDTO userDTO, BindingResult result, HttpServletRequest request) {
	    	JSONObject jsonResponse = new JSONObject();
	    	JSONObject jsonErrors = new JSONObject();
	    	
		    if (result.hasErrors()) { 
		    	
		        for (FieldError fieldError: result.getFieldErrors() ) {
		        	jsonErrors.put(fieldError.getField(), fieldError.getCode() );
		        }    
		        jsonResponse.put("fieldErrors", jsonErrors);   
		        return new ResponseEntity<>(jsonResponse, HttpStatus.BAD_REQUEST);
		    } 
	   	       	
            if (userRepository.findOneByLogin(userDTO.getLogin()) != null) {
            	jsonErrors.put("username", "DuplicateUsername");
                jsonResponse.put("fieldErrors", jsonErrors);        	
                return new ResponseEntity<>(jsonResponse, HttpStatus.BAD_REQUEST);
            } else {
            	if (userRepository.findOneByEmail(userDTO.getEmail()) != null) {
                	jsonErrors.put("email", "DuplicateEmail");
                    jsonResponse.put("fieldErrors", jsonErrors);        	
                    return new ResponseEntity<>(jsonResponse, HttpStatus.BAD_REQUEST);            		
            	}
            }

            User user = userService.createUserInformation(userDTO.getLogin(), userDTO.getPassword(),
            userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail().toLowerCase(),
            userDTO.getLangKey());
            
            /*
            String baseUrl = request.getScheme() + // "http"
            "://" +                            // "://"
            request.getServerName() +          // "myhost"
            ":" +                              // ":"
            request.getServerPort();           // "80"

            mailService.sendActivationEmail(user, baseUrl);
            */
            jsonResponse.put("message", "ok");
            return new ResponseEntity<>(jsonResponse, HttpStatus.CREATED);      
    }
    /**
     * GET  /activate -> activate the registered user.
     */
    @RequestMapping(value = "/activate",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<String> activateAccount(@RequestParam(value = "key") String key) {
        User user = userService.activateRegistration(key);
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<String>(HttpStatus.OK);
    }

    /**
     * GET  /authenticate -> check if the user is authenticated, and return its login.
     */
    @RequestMapping(value = "/authenticate",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public String isAuthenticated(HttpServletRequest request) {
        log.debug("REST request to check if the current user is authenticated");
        return request.getRemoteUser();
    }

    /**
     * GET  /account -> get the current user.
     */
    @RequestMapping(value = "/account",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<UserDTO> getAccount() {
        User user = userService.getUserWithAuthorities();
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        List<String> roles = new ArrayList<>();
        for (Authority authority : user.getAuthorities()) {
            roles.add(authority.getName());
        }
        return new ResponseEntity<>(
            new UserDTO(
                user.getLogin(),
                null,
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getLangKey(),
                roles),
            HttpStatus.OK);
    }

    /**
     * POST  /account -> update the current user information.
     */
    @RequestMapping(value = "/account",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<String> saveAccount(@RequestBody UserDTO userDTO) {
        User userHavingThisLogin = userRepository.findOneByLogin(userDTO.getLogin());
        if (userHavingThisLogin != null && !userHavingThisLogin.getLogin().equals(SecurityUtils.getCurrentLogin())) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        userService.updateUserInformation(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * POST  /change_password -> changes the current user's password
     */
    @RequestMapping(value = "/account/change_password",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<?> changePassword(@RequestBody String password) {
        if (StringUtils.isEmpty(password) || password.length() < 5 || password.length() > 50) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        userService.changePassword(password);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * GET  /account/sessions -> get the current open sessions.
     */
    @RequestMapping(value = "/account/sessions",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<PersistentToken>> getCurrentSessions() {
        User user = userRepository.findOneByLogin(SecurityUtils.getCurrentLogin());
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(
            persistentTokenRepository.findByUser(user),
            HttpStatus.OK);
    }

    /**
     * DELETE  /account/sessions?series={series} -> invalidate an existing session.
     *
     * - You can only delete your own sessions, not any other user's session
     * - If you delete one of your existing sessions, and that you are currently logged in on that session, you will
     *   still be able to use that session, until you quit your browser: it does not work in real time (there is
     *   no API for that), it only removes the "remember me" cookie
     * - This is also true if you invalidate your current session: you will still be able to use it until you close
     *   your browser or that the session times out. But automatic login (the "remember me" cookie) will not work
     *   anymore.
     *   There is an API to invalidate the current session, but there is no API to check which session uses which
     *   cookie.
     */
    @RequestMapping(value = "/account/sessions/{series}",
            method = RequestMethod.DELETE)
    @Timed
    public void invalidateSession(@PathVariable String series) throws UnsupportedEncodingException {
        String decodedSeries = URLDecoder.decode(series, "UTF-8");
        User user = userRepository.findOneByLogin(SecurityUtils.getCurrentLogin());
        List<PersistentToken> persistentTokens = persistentTokenRepository.findByUser(user);
        for (PersistentToken persistentToken : persistentTokens) {
            if (StringUtils.equals(persistentToken.getSeries(), decodedSeries)) {
                persistentTokenRepository.delete(decodedSeries);
            }
        }
    }
}
