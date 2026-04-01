package wonbin.financial.dto.oauth;

import lombok.Data;

@Data
public class MemberDto {
    String memberName;
    Long id;
    public  MemberDto(String memberName, Long id) {
        this.memberName = memberName;
        this.id=id;
    }
}
