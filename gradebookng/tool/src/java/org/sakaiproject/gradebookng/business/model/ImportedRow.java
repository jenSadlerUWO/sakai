package org.sakaiproject.gradebookng.business.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a row of the spreadsheet, contains a few fixed fields and then the map of cells
 */
public class ImportedRow implements Serializable {

	@Getter
	@Setter
	private String studentEid;

	@Getter
	@Setter
	private String studentUuid;

	@Getter
	@Setter
	private String studentName;

	@Getter
	@Setter
	private String studentNumber;

	@Getter
	@Setter
	private String anonID;

	@Getter
	private GbUser user;

	@Getter
	@Setter
	private Map<String, ImportedCell> cellMap;

	public ImportedRow() {
		this.cellMap = new HashMap<>();
	}

	public void setUser(GbUser gbUser) {
		user = gbUser;
	}
}
