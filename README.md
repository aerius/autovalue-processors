# AutoValue Serialization/Parser Processor

This is a project automating the process of writing parsers and serializers for simple @AutoValue objects.

See for more information about AutoValue: https://github.com/google/auto/blob/master/value/userguide/index.md

This suite of tools is designed for communication between a GWT client and any Spring/Micronaut-esque (jackson-compatible) service architecture. A project containing domain objects adhering to `@AutoValue`-style format and build-up is assumed to be shared between the client and service projects.

## Installation

### Shared

For the shared project, add the `-parsers` and `-serializers` projects in the `<dependencies>` section:

```xml
    <dependency>
      <groupId>nl.aerius</groupId>
      <artifactId>autovalue-annotations-parsers</artifactId>
      <version>1.0</version>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>nl.aerius</groupId>
      <artifactId>autovalue-annotations-serializers</artifactId>
      <version>1.0</version>
      <optional>true</optional>
    </dependency>
```

Then, in the `maven-compiler-plugin` section, add the `-processors` project to the `<annotationProcessorPaths>`:

```xml
<annotationProcessorPaths>
  <path>
    <groupId>nl.aerius</groupId>
    <artifactId>autovalue-processors</artifactId>
    <version>1.0</version>
  </path>
</annotationProcessorPaths>
```

In order not to spoil the `-client` and `-service` project with `-serializer` and `-parser` code, respectively, the `-shared` project needs to be packaged in an interesting way:

```xml
      <plugin>
        <artifactId>maven-jar-plugin</artifactId>
        <executions>
          <execution>
            <id>default-jar</id>
            <goals>
              <goal>jar</goal>
            </goals>
            <phase>package</phase>
            <configuration>
              <excludes>
                <exclude>**/*JsonParser*</exclude>
                <exclude>**/*JsonSerializer*</exclude>
              </excludes>
            </configuration>
          </execution>
          <execution>
            <id>serializers</id>
            <goals>
              <goal>jar</goal>
            </goals>
            <phase>package</phase>
            <configuration>
              <classifier>serializers</classifier>
              <includes>
                <include>**/*JsonSerializer*</include>
              </includes>
            </configuration>
          </execution>
          <execution>
            <id>parsers</id>
            <goals>
              <goal>jar</goal>
            </goals>
            <phase>package</phase>
            <configuration>
              <classifier>parsers</classifier>
              <includes>
                <include>**/*JsonParser*</include>
              </includes>
            </configuration>
          </execution>
        </executions>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-source-plugin</artifactId>
        <executions>
          <execution>
            <id>attach-sources</id>
            <goals>
              <goal>jar-no-fork</goal>
            </goals>
            <configuration>
              <excludes>
                <exclude>**/*JsonSerializer*</exclude>
              </excludes>
            </configuration>
          </execution>

          <execution>
            <id>sources</id>
            <goals>
              <goal>jar-no-fork</goal>
            </goals>
            <configuration>
              <classifier>sources</classifier>
            </configuration>
          </execution>
        </executions>
      </plugin>
```

This large packaging configuration will accomplish four goals:

- Package all user code as normal
- Package generated parsers in a .jar classified as `parsers`, usable by the `-client`
- Package generated serializers in a .jar classified as `serializers`, usable by the `-service`
- Package code sources in a .jar classified as `sources`, used to adequately satisfy the `gwt-maven-plugin` on the client.

In the shared project's GWT module, add the following to prevent Serializers' dependencies from being compiled:

```xml
  <source path="shared">
    <exclude name="**/*JsonSerializer**" />
  </source>
```

### Client

For the client project, which needs only the `-parsers` portion, exclude the `-serializers` portion of your shared project.

Given the shared project is named `nl.aerius:your-project-shared`, add the following to your its `<dependencies>` section:

```xml

    <dependency>
      <groupId>nl.aerius</groupId>
      <artifactId>your-project-shared</artifactId>
      <version>${project.version}</version>
      <exclusions>
        <exclusion>
          <groupId>nl.aerius</groupId>
          <artifactId>autovalue-annotations-serializers</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>nl.aerius</groupId>
      <artifactId>your-project-shared</artifactId>
      <version>${project.version}</version>
      <classifier>sources</classifier>
      <exclusions>
        <exclusion>
          <groupId>nl.aerius</groupId>
          <artifactId>autovalue-annotations-serializers</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>nl.aerius</groupId>
      <artifactId>your-project-shared</artifactId>
      <version>${project.version}</version>
      <classifier>parsers</classifier>
      <exclusions>
        <exclusion>
          <groupId>nl.aerius</groupId>
          <artifactId>autovalue-annotations-serializers</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
```

Add the following to the GWT Module:

```xml
  <inherits name='nl.aerius.wui.parser.AutoValueParsers' />
```

### Service

Similarly, add the following the the `-service`'s `<dependencies>` section:

```xml
    <dependency>
      <groupId>nl.aerius</groupId>
      <artifactId>your-project-shared</artifactId>
      <version>${project.version}</version>
      <exclusions>
        <exclusion>
          <groupId>nl.aerius</groupId>
          <artifactId>autovalue-annotations-parsers</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>nl.aerius</groupId>
      <artifactId>your-project-shared</artifactId>
      <version>${project.version}</version>
      <classifier>serializers</classifier>
    </dependency>
```

## Usage

Now, when installation is succesful, the processor may be used as follows.

For each `AutoValue` that implements the `Serializable` interface (thus marking its intent for transfer), a `*JsonParser` and `*JsonSerializer` will be generated. While generating these classes, it is assumed a `AutoValue.Builder` is available for the given `@AutoValue` type. Such an object type looks like this:

```java

@AutoValue
public abstract class Sample {
  public static Builder builder() {
    return new AutoValue_Sample.Builder();
  }

  public abstract String name();

  public abstract String value();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder name(String value);

    public abstract Builder value(String value);

    public abstract Sample build();
  }
}
```

### Client

Client parsers may be used in a similar way as described in the `gwt-client-common-json` project. (https://github.com/aerius/gwt-client-common-json#requests), except without having to write a substantial amount of boiler plate.

For each `@AutoValue` object type you wish to communicate between client and service, a `*JsonParser` class type is available, which may be used by `gwt-client-common-json`'s `RequestUtil` utility as follows:

```java
  public void fetchUser(final String id, final AsyncCallback<User> callback) {
    final String url = RequestUtil.prepareUrl("http://users.myapplication.xyz", "users/{id}", "{id}", id);

    RequestUtil.doGet(url, UserJsonParser::wrap, callback);
  }
```


### Service

#### Spring-Boot

For Spring-Boot projects, the Spring runtime needs to be made aware of the serializers. This can be done simply by making Spring scan the package that contains them, like so:

```
@Configuration
@ComponentScan("your.application.shared.domain")
public class YourApplicationConfig {}
```

#### Micronaut

For Micronaut projects, each generated serializers must (unfortunately) be explicitly mentioned, as follows:

```
@Factory
public final class SerializationFactoryImpl {
  @Bean
  public JsonSerializer<Foo> createFooJsonSerializer() {
    return new FooJsonSerializer();
  }

  @Bean
  public JsonSerializer<Bar> BarJsonSerializer() {
    return new BarJsonSerializer();
  }
}
```
