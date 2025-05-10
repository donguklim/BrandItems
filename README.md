# brand-item



## Implementation

Ktor 프레임워크를 베이스로 요구된 Rest API를 구현.


### MYSQL DB
H2를 베이스로 인메모리  MYSQL을 사용. 어플리케이션이 실행될때마다 과제 사항에 적혀있던 기본 데이터를 모두 인서트 함.
Exposed 라이브러리를 사용

Item, Brands, BrandsTotalPrice의 세 테이블이 있음.

#### Brands 테이블
다음과 같은 컬럼을 가지고 있음.

- id
- name

새로운 이름의 상품이 등록될때 새로운 row가 생성됨. 각 브랜드의 name은 유니크하게 되어있음.

#### BrandsTotalPrice 테이블

다음과 같은 컬럼을 가지고 있음.

- id
- brandId
- price

Brands와 one-to-one 관계이며, price는 각 항목의 가장 싼 해당 브랜드 상품의 가격의 합산 값을 저장하기 위한 용도의 테이블

새로운 상품이 추가되거나 삭제될때 해당하는 상품과 같은 브랜드와 항목에 속해 있는 상품중에 가장 싼 상품일 경우 테이블에 업데이트가 일어나게 되어있음.

price 값으로 indexing이 되어 있어서, 모든 항목의 상품을 단일 브랜드에서 구해야할때 가장 싼 브랜드가 무엇인지 이 테이블을 사용해 빠르게 찾을 수 있음.


#### Item 테이블의
다음과 같은 컬럼을 가지고 있음.

- brandId
- categoryId
- price

(brandId, categoryID, price)로 unique constraint가 걸려 있고, (categoryId, price)에 인덱스가 걸려있음.
각 카테고리는 enum을 사용되 지정된 아이디 값을 사용함.

groupby 오퍼레이션을 사용해 각 카테고리 별로 가장 싼 가격과 브랜드를 가져오기 위한 용도의 테이블




### Redis Cache

Simple Cache 플러그인을 사용해 모든 get request를 캐싱하는데 Redis를 사용함. 10초 주기로 갱신되도록 세팅함.

### Redis Lock

한 브랜드의 아이템을 추가할 시, BrandsTotalPrice 또한 아토믹하게 수정해야할 필요가 있을 수도 있음.
그렇기에 Redis를 각 브랜드 별로 Items, BrandTotalPrice MYSQL 테이블을 락킹하는데 사용함.

같은 브랜드의 상품은 다수의 유저가 등록/삭제 할시 순차적으로 등록 삭제가 이루어지게 된다.

Lettuce를 사용해 흔히쓰는 스핀 락이 아닌 pub/sub 기반의 락을 사용해 부하를 줄임.
Redisson에는 pub/sub기반의 락이 구현되어있지만 Lettuce에는 안되어 있어서 Lettuce에는 예전에 다른 프로젝트에서 구현한 pub/sub 기반의 락을 약간 수정해서 구현함.
Redisson에 pub/sub 기반의 락이 구현되어 있는데 Lettuce를 사용한 이유는 Lettuce가 실험적으로나마 Kotlin에서 코루틴을 지원해주는 유일한 Redis 라이브러리이기 때문.


### Integration Test
Exposed를 사용하는 부분과 레디스 락을 사용하는 부분만 일부 integration test를 구현함.


## 코드 빌드/실행

### DockerCompose를 사용해 빌드 후 실행

```shell
docker-compose build
docker-compose up
```

### DockerCompose를 사용한 테스트 빌드 와 실행

#### 빌드
```shell
docker-compose --profile test  build
```

#### 실행
```shell
docker-compose run --rm test
```
테스트 실행후 컨테이너가 종료 되도록 --rm 옵션을 추가함.



### 테스트를 도커 컨테이너가 밖에서 실행할 경우

```shell
./gradlew build
./gradlew test
```
gradlew를 사용해 빌드 후 테스트를 하면 되나, 환경 변수 `CACHE_REDIS_HOST`, `REDIS_PORT`에 각각 테스트에서 사용할 수 있는 Redis 호스트와 포트 넘버가 지정되어 있어야함.


### 도커 컨테이너가 밖에서 실행할 경우

```shell
./gradlew build
./gradlew run
```
테스트와 마찬가지로 gradlew를 사용해 빌드 후 실행이 가능하나, 환경 변수 `CACHE_REDIS_HOST`, `REDIS_PORT`에 각각 테스트에서 사용할 수 있는 Redis 호스트와 포트 넘버가 지정되어 있어야함.


## Swagger

로컬에서 실행시 [http://localhost:8080/openapi](http://localhost:8080/openapi) 주소로 Swagger 페이지를 열어서 API를 사용할 수 있음.


## 전체적인 구조 요약

- Items, Brands, BrandTotaPrice 등의 테이블에서 데이터가 보관되고 갱신됨.
- 모든 Get API는 Redis Cache를 사용하여 10초에 한번씩만 갱신되도록 되어 있음.
- 새로운 상품이 등록/삭제 될때 락을 걸려서 Items 테이블과 BrandTotalPrice 테이블의 integrity를 지키고 있음.