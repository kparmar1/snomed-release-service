package org.ihtsdo.buildcloud.controller;

import org.ihtsdo.buildcloud.controller.helper.HypermediaGenerator;
import org.ihtsdo.buildcloud.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;

@Controller
@RequestMapping("/login")
public class LoginController {

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private HypermediaGenerator hypermediaGenerator;

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity login(@RequestParam String username, @RequestParam String password, HttpServletRequest request) {
		String authenticationToken = authenticationService.authenticate(username, password);
		HashMap<String, String> response = new HashMap<>();
		if (authenticationToken != null) {
			response.put("authenticationToken", authenticationToken);
			return new ResponseEntity(response, HttpStatus.OK);
		} else {
			response.put("errorMessage", "Username or password are incorrect.");
			return new ResponseEntity(response, HttpStatus.BAD_REQUEST);
		}
	}

}
