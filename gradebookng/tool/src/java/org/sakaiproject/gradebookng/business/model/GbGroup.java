package org.sakaiproject.gradebookng.business.model;

import java.io.Serializable;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang.StringUtils;
import org.sakaiproject.site.api.Group;

/**
 * Represents a group or section
 *
 * @author Steve Swinsburg (steve.swinsburg@gmail.com)
 *
 */

@ToString
public class GbGroup implements Comparable<GbGroup>, Serializable {

	private static final long serialVersionUID = 1L;

	@Getter
	private final String id;

	@Getter
	private final String title;

	@Getter
	private final String reference;

	@Getter
	private final Type type;
	
	// OWL-2545  --bbailla2 
	@Getter 
	private final String providerId; 

	/**
	 * Type of group
	 */
	public enum Type {
		SECTION,
		GROUP,
		ALL;
	}

	private GbGroup(final String id, final String title, final String reference, final Type type, final String providerId) {
		this.id = id;
		this.title = title;
		this.reference = reference;
		this.type = type;
		this.providerId = providerId;
	}
	
	public static GbGroup fromGroup(Group g)
	{
		String provider = StringUtils.trimToEmpty(g.getProviderGroupId());
		Type type = provider.isEmpty() ? Type.GROUP : Type.SECTION;
		return new GbGroup(g.getId(), g.getTitle(), g.getReference(), type, provider);
	}
	
	public static GbGroup all(String title)
	{
		//return new GbGroup(null, title, null, Type.ALL, "");
		return new GbGroup("ALL", title, "", Type.ALL, "");
	}
	
	public boolean isSection()
	{
		return type == Type.SECTION;
	}

	@Override
	public int compareTo(final GbGroup other) {
		return new CompareToBuilder()
				.append(this.title, other.getTitle())
				.append(this.type, other.getType())
				.toComparison();

	}

	@Override
	public boolean equals(final Object o) {
		if (o == null) {
			return false;
		}
		if (o == this) {
			return true;
		}
		if (o.getClass() != getClass()) {
			return false;
		}
		final GbGroup other = (GbGroup) o;
		return new EqualsBuilder()
				.appendSuper(super.equals(o))
				.append(this.id, other.id)
				.append(this.title, other.title)
				.append(this.reference, other.reference)
				.append(this.type, other.type)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
				.append(this.id)
				.append(this.title)
				.append(this.reference)
				.append(this.type)
				.toHashCode();
	}

}
