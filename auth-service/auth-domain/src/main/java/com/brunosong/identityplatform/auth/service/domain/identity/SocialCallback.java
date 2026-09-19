package com.brunosong.identityplatform.auth.service.domain.identity;

/**
 * provider 가 브라우저를 어디로 돌려보냈는가.
 *
 * <p>이 값이 필요한 이유는 하나다. <b>code 를 토큰으로 바꿀 때 {@code redirect_uri} 를 한 번 더
 * 보내야 하고, 그 값이 인가 요청 때 쓴 것과 같아야 한다.</b> provider 는 "그때 그 요청이 맞느냐" 를
 * 그 값으로 대조한다. 그래서 어디로 돌아왔는지 모르면 교환을 할 수 없다.
 *
 * <p>주소 자체를 들고 다니지 않고 어느 쪽인지만 말한다. 주소는 설정이고, 그것을 아는 것은
 * provider 어댑터의 일이다. 호출자가 주소를 넘기게 두면 클라이언트가 시킨 값이 외부 요청에
 * 그대로 실리는 길이 생긴다.
 */
public enum SocialCallback {

    /**
     * provider 가 auth-service 로 돌려보냈다. 앱은 provider 를 알지 못한다.
     *
     * <p>provider 콘솔에 등록할 주소가 하나뿐이라 앱이 몇 개가 되든 그쪽 설정은 그대로다.
     * 대신 인증이 끝난 브라우저를 앱으로 돌려보내는 길을 우리가 만들어야 한다.
     */
    BROKER,

    /**
     * provider 가 앱으로 돌려보냈고, 앱이 받은 code 를 우리에게 넘겼다.
     *
     * <p>앱 페이지가 살아 있는 채로 돌아오므로 돌려보내는 길이 필요 없다. 대신 앱이 provider 를
     * 알아야 하고, provider 콘솔에는 앱마다 주소를 등록해야 한다.
     */
    APP
}
