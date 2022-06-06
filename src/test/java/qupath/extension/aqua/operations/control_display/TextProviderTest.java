package qupath.extension.aqua.operations.control_display;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextProviderTest {

	@Test
	void parseDoubleTest() {
		String s1 = "0.1";
		String s2 = "0.0";
		String s3 = ".0";
		assertFalse(Double.parseDouble(s1) == 0);
		assertTrue(Double.parseDouble(s2) == 0);
		assertTrue(Double.parseDouble(s3) == 0);
	}
	
	@Test
	void regexPercentTest() {
		String s1 = "100";
		String s2 = "-100";
		String s3 = "75.55";
		String s4 = "0";
		String regex = "^100$|^\\d{0,2}(\\.\\d{1,2})? *%?$";
		assertTrue(s1.matches(regex));
		assertFalse(s2.matches(regex));
		assertTrue(s3.matches(regex));
		assertTrue(s4.matches(regex));
		
	}
	
	@Test
	void regex0to1Test() {
		String s1 = "1";
		String s2 = "-1.0";
		String s3 = "1.0";
		String s4 = "0.0";
		String s5 = "0.1";
		String s6 = "0.12";
		String s7 = "0.90";
		String s8 = "00";
		String s9 = ".0";
		String s10 = ".";
		String regex = "^0(\\.\\d+)?$|^1(\\.0+)?$|^\\.[0-9](\\d+)?$";
		assertTrue(s1.matches(regex));
		assertFalse(s2.matches(regex));
		assertTrue(s3.matches(regex));
		assertTrue(s4.matches(regex));
		assertTrue(s5.matches(regex));
		assertTrue(s6.matches(regex));
		assertTrue(s7.matches(regex));
		assertFalse(s8.matches(regex));
		assertTrue(s9.matches(regex));
		assertFalse(s10.matches(regex));
	}

}
