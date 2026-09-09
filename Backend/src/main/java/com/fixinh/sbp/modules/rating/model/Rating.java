package com.fixinh.sbp.modules.rating.model;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fixinh.sbp.common.sharding.Region;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "ratings")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Rating {
	@Id private String id;
	private String bookingRef;
	@Indexed private String customerId;
	@Indexed private String serviceId;
	private String serviceName;
	private List<TechnicianSnapshot> technicians;
	private int overallScore;
	private String reviewComment;
	private List<SubcategoryScore> subcategoryScores;
	private List<String> evidenceUrls;
	private Instant submittedAt;
	private Instant deletedAt;
	private Region homeRegion;

	public record SubcategoryScore(String categoryName, int score) {}
	public record TechnicianSnapshot(String technicianId, String fullName) {}
}
