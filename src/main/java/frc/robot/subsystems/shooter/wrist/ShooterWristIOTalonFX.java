package frc.robot.subsystems.shooter.wrist;

import static frc.robot.subsystems.shooter.wrist.ShooterWristConstants.shooterWristMinAngle;
import static frc.robot.subsystems.shooter.wrist.ShooterWristConstants.wristMotorCANBus;
import static frc.robot.subsystems.shooter.wrist.ShooterWristConstants.wristMotorID;

import com.ctre.phoenix6.BaseStatusSignal;
import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.NeutralModeValue;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.ArmFeedforward;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.math.util.Units;
// import frc.robot.Constants.ElectricalLayout;
import org.littletonrobotics.junction.Logger;

public class ShooterWristIOTalonFX implements ShooterWristIO {
  // Motor and Encoders
  private TalonFX pivotMotor;
  private final ProfiledPIDController pidController;
  private ArmFeedforward feedforward = new ArmFeedforward(0, 0, 0, 0);
  private double setpoint = 0;

  private final StatusSignal<Double> appliedVolts;
  private final StatusSignal<Double> angleVelocityRadsPerSec;
  private final StatusSignal<Double> tempCelcius;
  private final StatusSignal<Double> currentAmps;
  private final StatusSignal<Double> angleRads;

  public ShooterWristIOTalonFX() {
    pivotMotor = new TalonFX(wristMotorID, wristMotorCANBus);

    var config = new TalonFXConfiguration();

    config.CurrentLimits.SupplyCurrentLimit = 30.0;
    config.CurrentLimits.SupplyCurrentLimitEnable = true;
    config.MotorOutput.NeutralMode = NeutralModeValue.Coast;

    pivotMotor.getConfigurator().apply(config);

    setBrake(true);
    pidController =
        new ProfiledPIDController(
            ShooterWristConstants.shooterWristPIDReal[0],
            ShooterWristConstants.shooterWristPIDReal[1],
            ShooterWristConstants.shooterWristPIDReal[2],
            new TrapezoidProfile.Constraints(2.45, 2.45));

    pidController.setTolerance(
        ShooterWristConstants.shooterWristPIDTolerance,
        ShooterWristConstants.shooterWristVelocityTolerance);
    pivotMotor.setPosition(
        Units.degreesToRotations(shooterWristMinAngle)); // Assume we boot at hard stop

    angleRads = pivotMotor.getPosition();
    angleVelocityRadsPerSec = pivotMotor.getVelocity();
    appliedVolts = pivotMotor.getSupplyVoltage();
    currentAmps = pivotMotor.getSupplyCurrent();
    tempCelcius = pivotMotor.getDeviceTemp();

    BaseStatusSignal.setUpdateFrequencyForAll(
        50, angleVelocityRadsPerSec, appliedVolts, currentAmps, tempCelcius, angleRads);
    // configurePID();
    // configureFeedForward();
  }

  /** Updates the set of loggable inputs. */
  @Override
  public void updateInputs(ShooterWristIOInputs inputs) {
    BaseStatusSignal.refreshAll(
        angleVelocityRadsPerSec, appliedVolts, currentAmps, tempCelcius, angleRads);
    inputs.setpointAngleRads = pidController.getSetpoint().position;
    inputs.angleRads = angleRads.getValueAsDouble();
    inputs.appliedVolts = appliedVolts.getValueAsDouble();
    inputs.currentAmps = currentAmps.getValueAsDouble();
    inputs.tempCelsius = tempCelcius.getValueAsDouble();
    Logger.recordOutput("ShooterWrist/MotorEncoder", angleRads.getValueAsDouble());
  }

  /** Run open loop at the specified voltage. */
  @Override
  public void setVoltage(double motorVolts) {
    Logger.recordOutput("ShooterWrist/AppliedVolts", motorVolts);
    pivotMotor.setVoltage(motorVolts);
  }

  /** Returns the current distance measurement. */
  @Override
  public double getAngle() {
    return (Units.rotationsToRadians(angleRads.getValueAsDouble()));
  }

  /** Go to Setpoint */
  @Override
  public void goToSetpoint(double setpoint) {
    pidController.setGoal(setpoint);
    // With the setpoint value we run PID control like normal
    double pidOutput = MathUtil.clamp(pidController.calculate(getAngle()), -3, 3);
    double feedforwardOutput =
        feedforward.calculate(getAngle(), pidController.getSetpoint().velocity);

    Logger.recordOutput("ShooterWrist/FeedforwardOutput", feedforwardOutput);
    Logger.recordOutput("ShooterWrist/PIDOutput", pidOutput);

    Logger.recordOutput("ShooterWrist/VelocityError", pidController.getVelocityError());

    setVoltage(MathUtil.clamp(pidOutput + feedforwardOutput, -4, 4));
  }

  @Override
  public void holdSetpoint(double setpoint) {
    goToSetpoint(setpoint);
  }

  @Override
  public void setBrake(boolean brake) {
    if (brake) {
      pivotMotor.setNeutralMode(NeutralModeValue.Coast);
    }
  }

  @Override
  public boolean atSetpoint() {
    return Math.abs(getAngle() - setpoint) < ShooterWristConstants.shooterWristPIDTolerance;
  }

  @Override
  public void setP(double p) {
    pidController.setP(p);
  }

  @Override
  public void setI(double i) {
    pidController.setI(i);
  }

  @Override
  public void setD(double d) {
    pidController.setD(d);
  }

  @Override
  public void setFF(double ff) {
    // pidController.setFF(ff);
  }

  @Override
  public void setkS(double kS) {
    feedforward = new ArmFeedforward(kS, feedforward.kg, feedforward.kv, feedforward.ka);
  }

  @Override
  public void setkG(double kG) {
    feedforward = new ArmFeedforward(feedforward.ks, kG, feedforward.kv, feedforward.ka);
  }

  @Override
  public void setkV(double kV) {
    feedforward = new ArmFeedforward(feedforward.ks, feedforward.kg, kV, feedforward.ka);
  }

  @Override
  public void setkA(double kA) {
    feedforward = new ArmFeedforward(feedforward.ks, feedforward.kg, feedforward.kv, kA);
  }

  @Override
  public double getkS() {
    return feedforward.ks;
  }

  @Override
  public double getkG() {
    return feedforward.kg;
  }

  @Override
  public double getkV() {
    return feedforward.kv;
  }

  @Override
  public double getkA() {
    return feedforward.ka;
  }

  @Override
  public double getP() {
    return pidController.getP();
  }

  @Override
  public double getI() {
    return pidController.getI();
  }

  @Override
  public double getD() {
    return pidController.getD();
  }

  @Override
  public double getFF() {
    // return pidController.getFF();
    return 0;
  }
}
